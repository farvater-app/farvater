package app.farvater

import android.app.Application
import android.media.AudioManager
import android.media.SoundPool
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import app.farvater.ui.screens.WitchPurr
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSoundPool
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, shadows = [WitchPurrTest.TrackingSoundPool::class])
class WitchPurrTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var pool: SoundPool
    private lateinit var purr: WitchPurr
    private lateinit var audio: AudioManager
    private var creations = 0
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private val shadow get() = Shadow.extract<TrackingSoundPool>(pool)

    @Before fun setUp() {
        audio = app.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        purr = WitchPurr(app) {
            creations++
            SoundPool.Builder().setMaxStreams(1).build().also { pool = it }
        }
        owner.registry.addObserver(purr)
    }

    @After fun tearDown() {
        owner.registry.removeObserver(purr)
        purr.release()
    }

    private fun start() {
        owner.registry.currentState = Lifecycle.State.STARTED
    }

    private fun loaded(success: Boolean = true) = shadow.notifyResourceLoaded(R.raw.witch_purr, success)

    @Test fun earlyRequestWaitsForSuccessfulLoad() {
        start()
        purr.play()
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
        loaded()
        assertEquals(1, shadow.getResourcePlaybacks(R.raw.witch_purr).size)
    }

    @Test fun failedOrLateLoadDoesNotPlayOldRequest() {
        start()
        purr.play()
        loaded(false)
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
        purr.play()
        ShadowSystemClock.advanceBy(Duration.ofMillis(501))
        loaded()
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
    }

    @Test fun stopCancelsPendingAndNeverResumesIt() {
        start()
        purr.play()
        owner.registry.currentState = Lifecycle.State.CREATED
        loaded()
        start()
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
        assertEquals(1, creations)
    }

    @Test fun stopEndsPlayingStreamAndRejectsBackgroundRequests() {
        start()
        loaded()
        purr.play()
        assertTrue(shadow.wasResourcePlayed(R.raw.witch_purr))
        owner.registry.currentState = Lifecycle.State.CREATED
        assertEquals(1, shadow.stops)
        shadow.clearPlayed()
        purr.play()
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
    }

    @Test fun releaseDiscardsLateCallbackAndCannotRecreatePool() {
        start()
        purr.play()
        val releasedPool = shadow
        purr.release()
        loaded()
        purr.play()
        purr.onStart(owner)
        assertEquals(1, releasedPool.releases)
        assertEquals(1, creations)
        assertFalse(releasedPool.wasResourcePlayed(R.raw.witch_purr))
    }

    @Test fun mutedRingerAndZeroMediaVolumeSuppressPlayback() {
        start()
        loaded()
        listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE).forEach {
            audio.ringerMode = it
            purr.play()
        }
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        purr.play()
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
    }

    @Test fun muteIsCheckedAgainAfterLoading() {
        start()
        purr.play()
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        loaded()
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertFalse(shadow.wasResourcePlayed(R.raw.witch_purr))
    }

    @Test fun poolIsCreatedOnlyWhenStartedAndReleasedOnce() {
        assertEquals(0, creations)
        purr.play()
        start()
        purr.onStart(owner)
        assertEquals(1, creations)
        val created = shadow
        purr.release()
        purr.release()
        assertEquals(1, created.releases)
    }

    @Implements(SoundPool::class)
    class TrackingSoundPool : ShadowSoundPool() {
        var stops = 0
        var releases = 0

        @Implementation fun stop(streamId: Int) {
            if (streamId != 0) stops++
        }

        @Implementation fun release() {
            releases++
        }
    }
}
