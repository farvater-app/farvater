package app.farvater

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.farvater.ui.screens.ProfileQr
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileQrTest {
    @Test fun selectedImageRoundTripsSubscriptionAndUnicodeProfile() {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        val uri = Uri.parse("content://test.provider/selected")
        for (text in listOf("https://subscription.example.test/feed", "vless://test@node.example.test:443?security=tls#Узел")) {
            val bitmap = requireNotNull(ProfileQr.encode(text))
            val png = ByteArrayOutputStream()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, png))
            bitmap.recycle()
            shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(png.toByteArray()))
            assertEquals(text, ProfileQr.payload(ProfileQr.read(resolver, uri)))
        }
    }

    @Test fun wrappedSubscriptionAndParsedLinksUseTheSameConfirmationPayload() {
        assertEquals("https://subscription.example.test/feed",
            ProfileQr.payload("farvater://import?url=https%3A%2F%2Fsubscription.example.test%2Ffeed"))
        assertNull(ProfileQr.payload("vless://broken"))
        assertNull(ProfileQr.payload("farvater://import?url=file%3A%2F%2F%2Funused"))
    }

    @Test fun invalidOrOversizedPayloadsAndFileUrisAreRejected() {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        for (value in listOf("intent://test", "https://localhost/test", "garbage", "x".repeat(5000))) {
            assertNull(ProfileQr.payload(value))
        }
        assertNull(ProfileQr.encode("x".repeat(5000)))
        assertNull(ProfileQr.read(resolver, Uri.parse("file:///unused")))
    }
}
