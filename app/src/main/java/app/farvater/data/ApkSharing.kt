package app.farvater.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import app.farvater.BuildConfig
import app.farvater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

object ApkSharing {
    private val nativeEntry = Regex("^lib/([A-Za-z0-9_-]+)/libgojni\\.so$")

    suspend fun architectures(context: Context): List<String> = withContext(Dispatchers.IO) {
        source(context).let(::readArchitectures)
    }

    suspend fun prepare(context: Context): Intent = withContext(Dispatchers.IO) {
        val architectures = readArchitectures(source(context))
        val uri = ApkProvider.uri(context)
        val abi = androidx.core.text.BidiFormatter.getInstance(AppLanguages.wrap(context).resources.configuration.layoutDirection == 1)
            .unicodeWrap(architectures.joinToString(", "), androidx.core.text.TextDirectionHeuristicsCompat.LTR)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.localizedString(R.string.share_apk_message, BuildConfig.VERSION_NAME, abi))
            clipData = ClipData.newRawUri(context.localizedString(R.string.app_name), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun clearLegacyCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, "share").deleteRecursively()
    }

    internal fun source(context: Context): File {
        val info = context.applicationInfo
        if (!info.splitSourceDirs.isNullOrEmpty()) throw LocalizedIOException(R.string.share_apk_split)
        return File(info.sourceDir)
    }

    internal fun readArchitectures(file: File): List<String> = ZipFile(file).use { zip ->
        zip.entries().asSequence().mapNotNull {
            nativeEntry.matchEntire(it.name)?.groupValues?.get(1)
        }.distinct().sorted().toList().also {
            if (it.isEmpty()) throw LocalizedIOException(R.string.share_apk_unavailable)
        }
    }

    internal fun fileName(version: String): String =
        "farvater-${version.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "unknown" }}.apk"
}
