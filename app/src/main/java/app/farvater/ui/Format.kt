package app.farvater.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.farvater.R
import java.util.Locale

@Composable
fun formatSpeed(bytesPerSecond: Long): String = formatSpeed(LocalContext.current, bytesPerSecond)

fun formatSpeed(context: Context, bytesPerSecond: Long): String =
    context.getString(R.string.unit_speed, formatBytes(context, bytesPerSecond))

@Composable
fun formatBytes(bytes: Long): String = formatBytes(LocalContext.current, bytes)

fun formatBytes(context: Context, bytes: Long): String {
    val localized = context
    val locale = localized.resources.configuration.locales[0].let { if (it.language == "ru") Locale.US else it }
    val b = bytes.coerceAtLeast(0).toDouble()
    val (unit, value) = when {
        b < 1024 -> R.string.unit_bytes to String.format(locale, "%d", b.toLong())
        b < 1024 * 1024 -> R.string.unit_kilobytes to String.format(locale, "%.0f", b / 1024)
        b < 1024.0 * 1024 * 1024 -> R.string.unit_megabytes to String.format(locale, "%.1f", b / 1024 / 1024)
        else -> R.string.unit_gigabytes to String.format(locale, "%.2f", b / 1024 / 1024 / 1024)
    }
    return localized.getString(unit, value)
}

@Composable
fun formatDuration(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, s) else String.format(locale, "%02d:%02d", m, s)
}

@Composable
fun formatAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0) return androidx.compose.ui.res.stringResource(R.string.updated_never)
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> androidx.compose.ui.res.stringResource(R.string.updated_now)
        minutes < 60 -> androidx.compose.ui.res.stringResource(R.string.updated_minutes, minutes)
        minutes < 60 * 24 -> androidx.compose.ui.res.stringResource(R.string.updated_hours, minutes / 60)
        else -> androidx.compose.ui.res.stringResource(R.string.updated_days, minutes / 60 / 24)
    }
}

private val LeadingFlag = Regex("""^\s*([\x{1F1E6}-\x{1F1FF}]{2})\s*""")

fun splitFlag(name: String): Pair<String?, String> {
    val match = LeadingFlag.find(name) ?: return null to name.trim()
    return match.groupValues[1] to name.substring(match.range.last + 1).trim().ifBlank { name.trim() }
}

@Composable
fun ltrText(value: String): String = androidx.core.text.BidiFormatter.getInstance(
    androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl,
).unicodeWrap(value, androidx.core.text.TextDirectionHeuristicsCompat.LTR)
