package app.farvater.data

import android.content.Context
import app.farvater.R

fun Context.importReason(reason: String): String = when (reason) {
    "unsupported_ss_method" -> localizedString(R.string.import_ss_method)
    "missing_reality_key" -> localizedString(R.string.import_reality_key)
    "unsupported_hysteria_obfs" -> localizedString(R.string.import_hysteria_obfs)
    "unsupported_nesting" -> localizedString(R.string.import_nesting)
    else -> reason
}

fun Context.importReasons(skipped: Map<String, Int>): String =
    skipped.entries.joinToString(", ") { (reason, count) -> "$count ${importReason(reason)}" }
