package io.github.dorumrr.happytaxes.data.local

/**
 * Constants for profile management.
 * 
 * TEMPORARY: These constants are used during migration to multi-profile architecture.
 * Will be replaced with proper profile context management in Phase 2-3.
 */
object ProfileConstants {
    /**
     * Default profile ID used for migration and initial setup.
     * This matches the ID used in MIGRATION_12_13.
     */
    const val DEFAULT_PROFILE_ID = "business-profile-default-uuid"
    
    /**
     * Profile type names.
     */
    const val PROFILE_NAME_BUSINESS = "Business"
    const val PROFILE_NAME_PERSONAL = "Personal"
    
    /**
     * Profile icons (Material 3).
     */
    const val PROFILE_ICON_BUSINESS = "business"
    const val PROFILE_ICON_PERSONAL = "person"
    
    /**
     * Profile colors (hex codes).
     */
    const val PROFILE_COLOR_BUSINESS = "#1976D2" // Blue
    const val PROFILE_COLOR_PERSONAL = "#388E3C" // Green
}

