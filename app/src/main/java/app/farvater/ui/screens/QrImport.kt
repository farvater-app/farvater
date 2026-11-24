package app.farvater.ui.screens

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.core.model.ProxyNode
import app.farvater.core.parser.LinkParser
import app.farvater.core.parser.SubscriptionLimitException
import app.farvater.core.parser.SubscriptionParser
import app.farvater.net.ImportLink
import app.farvater.ui.ios.AlertAction
import app.farvater.ui.ios.IosAlert
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source

internal object ProfileQr {
    fun payload(text: String?): String? {
        val value = text?.trim()?.takeIf { it.length in 1..4096 } ?: return null
        val uri = Uri.parse(value)
        val rawLink = if (uri.scheme == "farvater" && uri.host == "import") uri.getQueryParameter("url") else value
        ImportLink.sanitize(rawLink)?.let { link ->
            val scheme = link.substringBefore("://").lowercase()
            return if (scheme == "http" || scheme == "https") scheme + "://" + link.substringAfter("://")
                else link.takeIf { LinkParser.parse(it) != null }
        }
        if (!value.startsWith("{") && !value.startsWith("[")) return null
        return try {
            value.takeIf { SubscriptionParser.parse(it, ProxyNode.MANUAL_SOURCE).nodes.isNotEmpty() }
        } catch (_: SubscriptionLimitException) {
            null
        }
    }

    fun read(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        return runCatching {
            val data = resolver.openInputStream(uri)?.source()?.buffer()?.use {
                if (it.request(12L * 1024 * 1024 + 1)) return null
                it.readByteArray()
            } ?: return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            if (options.outWidth !in 1..32768 || options.outHeight !in 1..32768) return null
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1536) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: return null
            try { decode(bitmap) } finally { bitmap.recycle() }
        }.getOrNull()
    }

    fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
    }

    fun encode(value: String): Bitmap? {
        if (value.length !in 1..4096) return null
        return runCatching {
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val pixels = IntArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}

@Composable
internal fun QrImportRows(onImport: (String) -> Unit, onInvalid: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val camera = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { text ->
            scope.launch {
                withContext(Dispatchers.Default) { ProfileQr.payload(text) }?.let(onImport) ?: onInvalid()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val value = withContext(Dispatchers.IO) { ProfileQr.payload(ProfileQr.read(context.contentResolver, uri)) }
            value?.let(onImport) ?: onInvalid()
        }
    }
    IosDivider(start = 59.dp)
    IosRow(stringResource(R.string.qr_camera), icon = Icons.Rounded.QrCodeScanner, onClick = {
        camera.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
            .setBarcodeImageEnabled(false).setOrientationLocked(false))
    })
    IosDivider(start = 59.dp)
    IosRow(stringResource(R.string.qr_image), icon = Icons.Rounded.Image, onClick = { picker.launch("image/*") })
}

@Composable
internal fun ProfileQrDialog(value: String, onClose: () -> Unit) {
    var ready by androidx.compose.runtime.remember(value) { androidx.compose.runtime.mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, value) {
        this.value = withContext(Dispatchers.Default) { ProfileQr.encode(value) }
        ready = true
    }
    IosAlert(stringResource(R.string.qr_show), onClose,
        listOf(AlertAction(stringResource(R.string.action_close)) { onClose() }),
        content = {
            val code = bitmap
            if (code != null) Image(code.asImageBitmap(), stringResource(R.string.qr_show), Modifier.fillMaxWidth().aspectRatio(1f))
            else if (ready) Text(stringResource(R.string.qr_size))
            else app.farvater.ui.ios.IosSpinner()
        })
}
