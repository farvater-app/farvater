package app.farvater.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import app.farvater.BuildConfig
import java.io.File
import java.io.FileNotFoundException

class ApkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun source(uri: Uri): File {
        val context = requireNotNull(context)
        if (uri != uri(context)) throw FileNotFoundException("Unknown APK URI")
        return ApkSharing.source(context)
    }

    override fun getType(uri: Uri): String {
        source(uri)
        return "application/vnd.android.package-archive"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val file = source(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map {
                when (it) {
                    OpenableColumns.DISPLAY_NAME -> ApkSharing.fileName(BuildConfig.VERSION_NAME)
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only APK")
        return ParcelFileDescriptor.open(source(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        fun uri(context: Context): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.apk")
            .appendPath(ApkSharing.fileName(BuildConfig.VERSION_NAME))
            .build()
    }
}
