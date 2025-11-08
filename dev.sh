#!/bin/bash

# HappyTaxes Development Script
# Usage:
#   ./dev.sh install    - Uninstall, build, install, and launch app (fresh install)
#   ./dev.sh update     - Build, install, and launch app (preserves data like real update)
#   ./dev.sh screenshot - Take screenshot and open it
#   ./dev.sh aab        - Build release AAB for Google Play Store

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# App configuration
PACKAGE_NAME="io.github.dorumrr.happytaxes"
MAIN_ACTIVITY="${PACKAGE_NAME}/.MainActivity"
SCREENSHOT_DIR="screenshots"
KEYSTORE_PATH="release-keystore.jks"
KEY_ALIAS="happytaxes-release-key"

# Extract version from build.gradle.kts
APP_VERSION=$(grep 'versionName = ' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
APK_PATH_DEBUG="app/build/outputs/apk/debug/happytaxes-v${APP_VERSION}-debug.apk"
APK_PATH_RELEASE="app/build/outputs/apk/release/happytaxes-v${APP_VERSION}.apk"

# Helper functions
print_info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
}

print_success() {
    echo -e "${GREEN}✓ ${1}${NC}"
}

print_error() {
    echo -e "${RED}✗ ${1}${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
}

# Check if adb is available
check_adb() {
    if ! command -v adb &> /dev/null; then
        print_error "adb not found. Please install Android SDK Platform Tools."
        exit 1
    fi
}

# Find emulator command
find_emulator() {
    # Try common locations
    local emulator_paths=(
        "$HOME/Library/Android/sdk/emulator/emulator"
        "$ANDROID_HOME/emulator/emulator"
        "$ANDROID_SDK_ROOT/emulator/emulator"
    )

    for path in "${emulator_paths[@]}"; do
        if [ -f "$path" ]; then
            echo "$path"
            return 0
        fi
    done

    # Try to find in PATH
    if command -v emulator &> /dev/null; then
        echo "emulator"
        return 0
    fi

    return 1
}

# Start emulator
start_emulator() {
    print_info "No device connected. Starting emulator..."

    local emulator_cmd=$(find_emulator)
    if [ -z "$emulator_cmd" ]; then
        print_error "Could not find emulator command."
        print_info "Please install Android SDK or connect a physical device."
        exit 1
    fi

    # Get list of available AVDs
    local avds=$($emulator_cmd -list-avds)
    if [ -z "$avds" ]; then
        print_error "No emulators (AVDs) found."
        print_info "Please create an AVD in Android Studio or connect a physical device."
        exit 1
    fi

    # Prefer Android 15 emulators, then any available
    local preferred_avd=""

    # Try to find Pixel_9a (likely Android 15)
    if echo "$avds" | grep -q "Pixel_9a"; then
        preferred_avd="Pixel_9a"
    # Try to find Pixel_9_Pro_Fold (likely Android 15)
    elif echo "$avds" | grep -q "Pixel_9_Pro_Fold"; then
        preferred_avd="Pixel_9_Pro_Fold"
    # Try to find any Pixel 9 variant
    elif echo "$avds" | grep -q "Pixel_9"; then
        preferred_avd=$(echo "$avds" | grep "Pixel_9" | head -1)
    # Fall back to first available AVD
    else
        preferred_avd=$(echo "$avds" | head -1)
    fi

    print_info "Starting emulator: ${preferred_avd}"

    # Start emulator in background
    $emulator_cmd -avd "$preferred_avd" -no-snapshot-load > /dev/null 2>&1 &
    local emulator_pid=$!

    print_info "Waiting for emulator to boot (this may take 30-90 seconds)..."

    # Wait for emulator to be online (max 180 seconds)
    local timeout=180
    local elapsed=0
    local device_online=false

    # First, wait for device to appear
    while [ $elapsed -lt $timeout ]; do
        local device_count=$(adb devices | grep -v "List" | grep "device$" | wc -l)
        if [ "$device_count" -gt 0 ]; then
            device_online=true
            break
        fi
        sleep 2
        elapsed=$((elapsed + 2))

        # Show progress every 10 seconds
        if [ $((elapsed % 10)) -eq 0 ]; then
            print_info "Waiting for emulator to appear... (${elapsed}s elapsed)"
        fi
    done

    if [ "$device_online" = false ]; then
        print_error "Emulator failed to appear within ${timeout} seconds."
        exit 1
    fi

    print_info "Emulator online, waiting for boot to complete..."

    # Now wait for boot to complete (check boot_completed property)
    while [ $elapsed -lt $timeout ]; do
        local boot_completed=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
        if [ "$boot_completed" = "1" ]; then
            print_success "Emulator fully booted!"
            # Give it a few more seconds to settle
            sleep 3
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))

        # Show progress every 10 seconds
        if [ $((elapsed % 10)) -eq 0 ]; then
            print_info "Waiting for boot to complete... (${elapsed}s elapsed)"
        fi
    done

    print_error "Emulator failed to boot within ${timeout} seconds."
    exit 1
}

# Check if device is connected
check_device() {
    local device_count=$(adb devices | grep -v "List" | grep "device$" | wc -l)

    if [ "$device_count" -eq 0 ]; then
        # No device connected, try to start emulator
        start_emulator
        # Re-check after starting emulator
        device_count=$(adb devices | grep -v "List" | grep "device$" | wc -l)
        if [ "$device_count" -eq 0 ]; then
            print_error "No device/emulator available."
            exit 1
        fi
    elif [ "$device_count" -gt 1 ]; then
        print_warning "Multiple devices detected. Using first available device."
    fi

    local device_name=$(adb devices | grep -v "List" | grep "device$" | head -1 | awk '{print $1}')
    print_success "Device connected: ${device_name}"
}

# Install command (fresh install - removes all data)
install_app() {
    print_info "Starting fresh installation..."

    # Check prerequisites
    check_adb
    check_device

    # Uninstall existing app
    print_info "Uninstalling existing app..."
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        adb uninstall "$PACKAGE_NAME" 2>/dev/null || true
        print_success "App uninstalled"
    else
        print_info "App not installed, skipping uninstall"
    fi

    # Build debug APK
    print_info "Building debug APK..."
    ./gradlew assembleDebug
    print_success "Build complete"

    # Install APK
    print_info "Installing app..."
    ./gradlew installDebug
    print_success "App installed"

    # Launch app
    print_info "Launching app..."
    adb shell am start -n "$MAIN_ACTIVITY"
    print_success "App launched"

    echo ""
    print_success "Fresh installation complete! App is running on device."
    print_warning "Note: All previous data has been removed. Use './dev.sh update' to preserve data."
}

# Update command (preserves data like a real app update)
update_app() {
    print_info "Starting app update (data will be preserved)..."

    # Check prerequisites
    check_adb
    check_device

    # Check if app is currently installed
    local app_installed=false
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        app_installed=true
        print_info "App is currently installed - data will be preserved"
    else
        print_warning "App not currently installed - this will be a fresh install"
    fi

    # Build debug APK
    print_info "Building debug APK..."
    ./gradlew assembleDebug
    print_success "Build complete"

    # Install APK with -r flag to preserve data (like a real update)
    print_info "Installing app update..."
    if [ "$app_installed" = true ]; then
        # Use -r flag to reinstall and preserve data
        adb install -r "$APK_PATH_DEBUG"
        print_success "App updated (data preserved)"
    else
        # First install
        ./gradlew installDebug
        print_success "App installed (first install)"
    fi

    # Launch app
    print_info "Launching app..."
    adb shell am start -n "$MAIN_ACTIVITY"
    print_success "App launched"

    echo ""
    if [ "$app_installed" = true ]; then
        print_success "Update complete! App is running with preserved data."
        print_info "Database migrations will run automatically if needed."
        print_info "All transactions, receipts, and settings should be intact."
    else
        print_success "Installation complete! App is running on device."
    fi
}

# Screenshot command
take_screenshot() {
    print_info "Taking screenshot..."
    
    # Check prerequisites
    check_adb
    check_device
    
    # Create screenshots directory if it doesn't exist
    mkdir -p "$SCREENSHOT_DIR"
    
    # Find next screenshot number
    local next_num=0
    if [ -d "$SCREENSHOT_DIR" ] && [ "$(ls -A $SCREENSHOT_DIR)" ]; then
        # Find highest numbered screenshot
        local max_num=$(ls "$SCREENSHOT_DIR" | grep -E '^[0-9]+\.png$' | sed 's/\.png$//' | sort -n | tail -1)
        if [ -n "$max_num" ]; then
            next_num=$((max_num + 1))
        fi
    fi
    
    local screenshot_name="${next_num}.png"
    local screenshot_path="${SCREENSHOT_DIR}/${screenshot_name}"
    local device_path="/sdcard/screenshot_temp.png"
    
    # Take screenshot on device
    print_info "Capturing screen..."
    adb shell screencap -p "$device_path"
    
    # Pull screenshot to local machine
    print_info "Downloading screenshot..."
    adb pull "$device_path" "$screenshot_path" > /dev/null 2>&1
    
    # Clean up device
    adb shell rm "$device_path" > /dev/null 2>&1
    
    print_success "Screenshot saved: ${screenshot_path}"
    
    # Open screenshot
    print_info "Opening screenshot..."
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        open "$screenshot_path"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        if command -v xdg-open &> /dev/null; then
            xdg-open "$screenshot_path"
        elif command -v gnome-open &> /dev/null; then
            gnome-open "$screenshot_path"
        else
            print_warning "Could not open screenshot automatically. Please open manually: ${screenshot_path}"
        fi
    else
        print_warning "Could not open screenshot automatically. Please open manually: ${screenshot_path}"
    fi
    
    print_success "Screenshot #${next_num} complete!"
}

# Check if keystore exists
check_keystore() {
    if [ ! -f "$KEYSTORE_PATH" ]; then
        print_error "Keystore not found: $KEYSTORE_PATH"
        print_info "You need to create a keystore first."
        print_info "IF YOU ALREADY HAVE ONE FOR THIS PROJECT, DROP IT ON ROOT!"
        print_info "Otherwise, run: ./dev.sh create-keystore"
        print_warning "Make sure you BACKUP the keystore afterwards!"
        exit 1
    fi
    print_success "Keystore found: $KEYSTORE_PATH"
}

# Validate keystore.properties configuration
validate_keystore_properties() {
    local KEYSTORE_PROPS="keystore.properties"

    if [ ! -f "$KEYSTORE_PROPS" ]; then
        print_error "Missing: $KEYSTORE_PROPS"
        echo ""
        print_info "The keystore.properties file is required for release builds."
        print_info "This file should contain:"
        echo "  • storeFile=release-keystore.jks"
        echo "  • storePassword=your_store_password"
        echo "  • keyAlias=happytaxes-release-key"
        echo "  • keyPassword=your_key_password"
        echo ""
        print_info "To fix this:"
        echo "  1. If you have an existing keystore:"
        echo "     ./dev.sh populate-keystore-properties"
        echo ""
        echo "  2. If you need to create a new keystore:"
        echo "     ./dev.sh create-keystore"
        echo ""
        exit 1
    fi

    # Validate required properties
    local missing_props=()

    # Check if properties exist and are not empty
    local store_file=$(grep "^storeFile=" "$KEYSTORE_PROPS" | cut -d'=' -f2-)
    local store_password=$(grep "^storePassword=" "$KEYSTORE_PROPS" | cut -d'=' -f2-)
    local key_alias=$(grep "^keyAlias=" "$KEYSTORE_PROPS" | cut -d'=' -f2-)
    local key_password=$(grep "^keyPassword=" "$KEYSTORE_PROPS" | cut -d'=' -f2-)

    if [ -z "$store_file" ]; then
        missing_props+=("storeFile")
    fi
    if [ -z "$store_password" ]; then
        missing_props+=("storePassword")
    fi
    if [ -z "$key_alias" ]; then
        missing_props+=("keyAlias")
    fi
    if [ -z "$key_password" ]; then
        missing_props+=("keyPassword")
    fi

    if [ ${#missing_props[@]} -gt 0 ]; then
        print_error "Invalid $KEYSTORE_PROPS - missing properties:"
        for prop in "${missing_props[@]}"; do
            echo "  • $prop"
        done
        echo ""
        print_info "To fix this:"
        echo "  ./dev.sh populate-keystore-properties"
        echo ""
        exit 1
    fi

    print_success "keystore.properties is valid"
}

# Populate keystore.properties with credentials
populate_keystore_properties() {
    print_info "Populate keystore.properties"
    echo ""

    # Check if keystore exists
    if [ ! -f "$KEYSTORE_PATH" ]; then
        print_error "Keystore not found: $KEYSTORE_PATH"
        print_info "You need to create a keystore first!"
        print_info "Run: ./dev.sh create-keystore"
        exit 1
    fi

    local KEYSTORE_PROPS="keystore.properties"

    # Backup existing file if it exists
    if [ -f "$KEYSTORE_PROPS" ]; then
        print_warning "Existing $KEYSTORE_PROPS found"
        read -p "Do you want to overwrite it? (yes/no): " confirm
        if [ "$confirm" != "yes" ]; then
            print_info "Operation cancelled"
            exit 0
        fi
        cp "$KEYSTORE_PROPS" "${KEYSTORE_PROPS}.backup"
        print_info "Backed up to ${KEYSTORE_PROPS}.backup"
    fi

    echo ""
    print_info "Please enter your keystore credentials:"
    echo ""

    # Prompt for passwords
    read -sp "Enter keystore password: " STORE_PASS
    echo ""
    read -sp "Enter key password (press Enter if same as keystore): " KEY_PASS
    echo ""

    if [ -z "$KEY_PASS" ]; then
        KEY_PASS="$STORE_PASS"
    fi

    # Verify credentials by trying to list the keystore
    print_info "Verifying credentials..."
    if ! keytool -list -keystore "$KEYSTORE_PATH" -storepass "$STORE_PASS" -alias "$KEY_ALIAS" -keypass "$KEY_PASS" &>/dev/null; then
        print_error "Invalid credentials! Could not access keystore."
        print_info "Please check your passwords and try again."
        # Clear passwords from memory
        STORE_PASS=""
        KEY_PASS=""
        exit 1
    fi

    print_success "Credentials verified!"

    # Create keystore.properties file
    cat > "$KEYSTORE_PROPS" << EOF
# Keystore configuration for release signing
# This file is gitignored - never commit it to version control!
# Generated by dev.sh on $(date)

storeFile=release-keystore.jks
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF

    # Clear passwords from memory
    STORE_PASS=""
    KEY_PASS=""

    if [ -f "$KEYSTORE_PROPS" ]; then
        echo ""
        print_success "Created $KEYSTORE_PROPS"
        echo ""
        print_warning "IMPORTANT: Backup this file securely!"
        echo "  • $KEYSTORE_PROPS (credentials)"
        echo ""
        print_info "You can now build releases with:"
        echo "  • ./dev.sh release"
        echo "  • ./gradlew assembleRelease"
    else
        print_error "Failed to create $KEYSTORE_PROPS"
        exit 1
    fi
}

# Build AAB command (for Google Play Store)
build_aab() {
    print_info "Building release AAB for Google Play Store..."

    # Build release AAB
    print_info "Running bundleRelease task..."
    ./gradlew bundleRelease

    local aab_path="app/build/outputs/bundle/release/app-release.aab"

    if [ -f "$aab_path" ]; then
        print_success "AAB built successfully!"
        echo ""
        print_info "AAB Location: ${aab_path}"

        # Get file size
        local file_size=$(du -h "$aab_path" | cut -f1)
        print_info "File Size: ${file_size}"

        echo ""
        print_success "Next Steps:"
        echo "  1. Sign the AAB with your release keystore (if not already signed)"
        echo "  2. Upload to Google Play Console"
        echo "  3. Enroll in Play App Signing (first upload only)"
        echo ""
        print_warning "Note: This AAB is for Google Play Store only."
        print_warning "      For testing, use './dev.sh install' or './dev.sh update'"
    else
        print_error "AAB build failed. Check the output above for errors."
        exit 1
    fi
}

# Create keystore
create_keystore() {
    print_info "Creating Release Keystore"
    echo ""

    if [ -f "$KEYSTORE_PATH" ]; then
        print_warning "Keystore already exists: $KEYSTORE_PATH"
        read -p "Do you want to overwrite it? (yes/no): " confirm
        if [ "$confirm" != "yes" ]; then
            print_info "Keystore creation cancelled"
            exit 0
        fi
        print_warning "Backing up existing keystore to ${KEYSTORE_PATH}.backup"
        cp "$KEYSTORE_PATH" "${KEYSTORE_PATH}.backup"
    fi

    print_info "This will create a new keystore for signing release APKs"
    print_warning "IMPORTANT: Keep this keystore and password safe!"
    print_warning "You'll need them to sign future updates"
    echo ""

    print_info "You'll be asked for:"
    echo "  1. Keystore password (minimum 6 characters)"
    echo "  2. Key password (minimum 6 characters, can be same as keystore password)"
    echo "  3. Your name and organization details"
    echo ""

    # Prompt for keystore password with validation
    while true; do
        read -s -p "Enter keystore password (min 6 chars): " STORE_PASS
        echo ""
        if [ ${#STORE_PASS} -lt 6 ]; then
            print_error "Password must be at least 6 characters!"
            continue
        fi
        read -s -p "Confirm keystore password: " STORE_PASS_CONFIRM
        echo ""
        if [ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]; then
            print_error "Passwords don't match!"
            continue
        fi
        break
    done

    # Prompt for key password with validation
    echo ""
    read -p "Use same password for key? (yes/no, default: yes): " USE_SAME
    if [ "$USE_SAME" = "no" ]; then
        while true; do
            read -s -p "Enter key password (min 6 chars): " KEY_PASS
            echo ""
            if [ ${#KEY_PASS} -lt 6 ]; then
                print_error "Password must be at least 6 characters!"
                continue
            fi
            read -s -p "Confirm key password: " KEY_PASS_CONFIRM
            echo ""
            if [ "$KEY_PASS" != "$KEY_PASS_CONFIRM" ]; then
                print_error "Passwords don't match!"
                continue
            fi
            break
        done
    else
        KEY_PASS="$STORE_PASS"
    fi

    echo ""
    print_info "Generating keystore..."
    echo ""

    # Generate keystore
    keytool -genkey -v \
        -keystore "$KEYSTORE_PATH" \
        -alias "$KEY_ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass "$STORE_PASS" \
        -keypass "$KEY_PASS"

    if [ ! -f "$KEYSTORE_PATH" ]; then
        print_error "Keystore creation failed!"
        # Clear passwords from memory
        STORE_PASS=""
        KEY_PASS=""
        STORE_PASS_CONFIRM=""
        KEY_PASS_CONFIRM=""
        exit 1
    fi

    echo ""
    print_success "Keystore created successfully: $KEYSTORE_PATH"

    # Create keystore.properties file (standard Android approach)
    local KEYSTORE_PROPS="keystore.properties"
    print_info "Creating $KEYSTORE_PROPS file..."

    cat > "$KEYSTORE_PROPS" << EOF
# Keystore configuration for release signing
# This file is gitignored - never commit it to version control!
# Generated by dev.sh on $(date)

storeFile=release-keystore.jks
storePassword=$STORE_PASS
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASS
EOF

    if [ -f "$KEYSTORE_PROPS" ]; then
        print_success "Created $KEYSTORE_PROPS"
        echo ""
        print_warning "IMPORTANT: Backup these files securely!"
        echo "  • $KEYSTORE_PATH (keystore file)"
        echo "  • $KEYSTORE_PROPS (credentials)"
        echo ""
        print_info "With keystore.properties, you can now build releases with:"
        echo "  • ./dev.sh release (builds signed APK)"
        echo "  • ./gradlew assembleRelease (direct Gradle build)"
    else
        print_error "Failed to create $KEYSTORE_PROPS"
    fi

    # Clear passwords from memory
    STORE_PASS=""
    KEY_PASS=""
    STORE_PASS_CONFIRM=""
    KEY_PASS_CONFIRM=""

    echo ""
    print_info "Next step: ./dev.sh release"
}

# Get production keystore SHA256
get_production_sha256() {
    if [ ! -f "$KEYSTORE_PATH" ]; then
        print_error "Production keystore not found: $KEYSTORE_PATH"
        return 1
    fi

    # Read password from keystore.properties
    local KEYSTORE_PROPS="keystore.properties"
    if [ ! -f "$KEYSTORE_PROPS" ]; then
        print_error "keystore.properties not found"
        return 1
    fi

    local STORE_PASSWORD=$(grep "^storePassword=" "$KEYSTORE_PROPS" | cut -d'=' -f2-)

    if [ -z "$STORE_PASSWORD" ]; then
        print_error "storePassword not found in keystore.properties"
        return 1
    fi

    # Get SHA256 with colons (using password from keystore.properties)
    local sha256_with_colons=$(keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass "$STORE_PASSWORD" 2>/dev/null | grep "SHA256:" | head -1 | sed 's/.*SHA256: //')

    # Convert to lowercase without colons (for F-Droid YAML)
    local sha256_lowercase=$(echo "$sha256_with_colons" | tr -d ':' | tr '[:upper:]' '[:lower:]')

    echo "$sha256_lowercase"
}

# Build and sign release APK (complete workflow)
build_release() {
    print_info "Building and Signing Release APK"
    echo ""

    # Validate keystore.properties first
    validate_keystore_properties

    # Check keystore exists
    check_keystore

    # Build release APK (Gradle signs it automatically with keystore.properties)
    print_info "Building release APK..."
    ./gradlew assembleRelease --no-daemon

    if [ ! -f "$APK_PATH_RELEASE" ]; then
        print_error "Release APK build failed! File not found: $APK_PATH_RELEASE"
        exit 1
    fi

    # Verify signature
    local android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -z "$android_sdk" ]; then
        android_sdk="$HOME/Library/Android/sdk"
    fi
    local build_tools_dir=$(find "$android_sdk/build-tools" -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1)
    local apksigner="$build_tools_dir/apksigner"

    if [ -f "$apksigner" ]; then
        print_info "Verifying signature..."
        "$apksigner" verify "$APK_PATH_RELEASE"
        print_success "APK signature verified!"
    fi

    local filesize=$(ls -lh "$APK_PATH_RELEASE" | awk '{print $5}')
    echo ""
    print_success "Release build complete!"
    print_info "Signed APK: $APK_PATH_RELEASE"
    print_info "Size: $filesize"

    # Get production SHA256
    local production_sha256=$(get_production_sha256)

    if [ -n "$production_sha256" ]; then
        echo ""
        print_info "Production keystore SHA256: $production_sha256"
    fi
    echo ""
}

# Show usage
show_usage() {
    echo "HappyTaxes Development Script"
    echo ""
    echo "Usage:"
    echo "  ./dev.sh install                     - Uninstall, build, install, and launch app (fresh install)"
    echo "  ./dev.sh update                      - Build, install, and launch app (preserves data)"
    echo "  ./dev.sh screenshot                  - Take screenshot and open it"
    echo "  ./dev.sh release                     - Build signed release APK"
    echo "  ./dev.sh aab                         - Build release AAB for Google Play Store"
    echo ""
    echo "Keystore Management:"
    echo "  ./dev.sh create-keystore             - Create production keystore (first time)"
    echo "  ./dev.sh populate-keystore-properties - Create keystore.properties file"
    echo "  ./dev.sh validate-keystore-properties - Validate keystore.properties"
    echo ""
    echo "Examples:"
    echo "  ./dev.sh install              # Fresh install - removes all data"
    echo "  ./dev.sh update               # Update install - preserves transactions, receipts, settings"
    echo "  ./dev.sh screenshot           # Take screenshot #0, #1, #2, etc."
    echo "  ./dev.sh release              # Build signed APK for F-Droid, direct distribution"
    echo "  ./dev.sh aab                  # Build AAB for Play Store"
    echo ""
    echo "Data Preservation:"
    echo "  install  - Uninstalls app first (all data lost)"
    echo "  update   - Uses 'adb install -r' to preserve data like real Play Store update"
    echo "           - Database migrations run automatically"
    echo "           - Transactions, receipts, and preferences preserved"
    echo ""
    echo "Release Builds:"
    echo "  release  - Builds signed APK (.apk) for F-Droid and direct distribution"
    echo "           - Requires keystore (create with: ./dev.sh create-keystore)"
    echo "           - Output: app/build/outputs/apk/release/happytaxes-v*.apk"
    echo ""
    echo "  aab      - Builds Android App Bundle (.aab) for Google Play Store"
    echo "           - Required format for Play Store (APK not accepted)"
    echo "           - Output: app/build/outputs/bundle/release/app-release.aab"
    echo ""
}

# Main script logic
main() {
    if [ $# -eq 0 ]; then
        show_usage
        exit 1
    fi

    case "$1" in
        install)
            install_app
            ;;
        update)
            update_app
            ;;
        screenshot)
            take_screenshot
            ;;
        release)
            build_release
            ;;
        aab)
            build_aab
            ;;
        create-keystore)
            create_keystore
            ;;
        populate-keystore-properties)
            populate_keystore_properties
            ;;
        validate-keystore-properties)
            validate_keystore_properties
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            print_error "Unknown command: $1"
            echo ""
            show_usage
            exit 1
            ;;
    esac
}

# Run main function
main "$@"

