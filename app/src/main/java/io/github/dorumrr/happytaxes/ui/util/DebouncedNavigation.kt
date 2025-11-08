package io.github.dorumrr.happytaxes.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Creates a debounced navigation callback to prevent rapid double-clicks.
 *
 * This utility prevents navigation issues caused by accidental double-taps or
 * touch sensitivity problems. When the returned callback is invoked, it will:
 * 1. Execute the navigation action immediately
 * 2. Ignore any subsequent calls for the specified debounce period
 * 3. Reset after the debounce period to allow future navigation
 *
 * Example usage:
 * ```
 * val debouncedBack = rememberDebouncedNavigation(
 *     onNavigate = { navController.popBackStack() }
 * )
 *
 * IconButton(onClick = debouncedBack) {
 *     Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
 * }
 * ```
 *
 * @param onNavigate The navigation action to execute (e.g., navController.popBackStack())
 * @param debounceMillis The debounce period in milliseconds (default: 500ms)
 * @param tag Optional tag for logging (useful for debugging specific screens)
 * @return A debounced callback that can be used as an onClick handler
 */
@Composable
fun rememberDebouncedNavigation(
    onNavigate: () -> Unit,
    debounceMillis: Long = 500,
    tag: String? = null
): () -> Unit {
    val coroutineScope = rememberCoroutineScope()
    var isNavigating by remember { mutableStateOf(false) }

    return remember(onNavigate, debounceMillis) {
        {
            if (!isNavigating) {
                tag?.let { android.util.Log.d(it, "Navigation triggered") }
                isNavigating = true
                coroutineScope.launch {
                    onNavigate()
                    // Reset after debounce period to prevent rapid re-clicks
                    delay(debounceMillis)
                    isNavigating = false
                }
            } else {
                tag?.let { android.util.Log.d(it, "Navigation ignored (already navigating)") }
            }
        }
    }
}

