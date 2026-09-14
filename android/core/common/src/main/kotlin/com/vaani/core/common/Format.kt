package com.vaani.core.common

/** Formats a millisecond offset as mm:ss or h:mm:ss (mono display). */
fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

/** Formats a duration as HH:MM:SS for the note meta / player total. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Human byte size, e.g. 47 MB. */
fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1) "%.0f MB".format(mb) else "%.0f KB".format(bytes.toDouble() / 1024)
}
