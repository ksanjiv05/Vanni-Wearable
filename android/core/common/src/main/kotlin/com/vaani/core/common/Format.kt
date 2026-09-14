package com.vaani.core.common

import java.util.Locale

/**
 * Technical/mono display values are always ASCII, locale-independent: formatted
 * with [Locale.ROOT] so digits and decimal separators never localise (an
 * India-first app must not render Arabic-Indic digits or comma decimals in
 * timestamps / byte counts).
 */

/** Formats a millisecond offset as mm:ss or h:mm:ss (mono display). */
fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

/** Formats a duration as HH:MM:SS for the note meta / player total. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
}

/** Human byte size, e.g. 47 MB. */
fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1) {
        String.format(Locale.ROOT, "%.0f MB", mb)
    } else {
        String.format(Locale.ROOT, "%.0f KB", bytes.toDouble() / 1024)
    }
}
