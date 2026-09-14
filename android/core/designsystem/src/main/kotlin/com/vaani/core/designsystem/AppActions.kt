package com.vaani.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide "show a transient message" hook, provided once at the app shell
 * (a themed Snackbar) and consumed by any screen without callback plumbing.
 * Used for actions whose real backend lands in a later milestone so buttons
 * still give immediate, honest feedback instead of doing nothing.
 */
val LocalShowMessage = staticCompositionLocalOf<(String) -> Unit> { {} }
