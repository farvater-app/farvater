package app.farvater.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.farvater.BuildConfig
import app.farvater.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object ApkSharing {
    private val mutex = Mutex()
    private val nativeEntry = Regex("^lib/([A-Za-z0-9_-]+)/libgojni\\.so$")

    suspend fun architectures(context: Context): List<String> = withContext(Dispatchers.IO) {
        source(context).let(::readArchitectures)
    }

    suspend fun prepare(context: Context): Intent = mutex.withLock {
        withContext(Dispatchers.IO) {
            val source = source(context)
            val architectures = readArchitectures(source)
            val directory = File(context.cacheDir, "share").apply { mkdirs() }
            directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
            val target = File(directory, fileName(BuildConfig.VERSION_NAME))
            val temporary = File(directory, "${target.name}.part")
            try {
                source.inputStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            directory.listFiles()?.filter { it != target && it.extension == "apk" && System.currentTimeMillis() - it.lastModified() > 86_400_000 }
                ?.forEach { it.delete() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
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
    }

    private fun source(context: Context): File {
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
