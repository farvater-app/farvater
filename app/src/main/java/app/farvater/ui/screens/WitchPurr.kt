package app.farvater.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.farvater.R

internal class WitchPurr(
    private val context: Context,
    private val createPool: () -> SoundPool = {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    },
) : DefaultLifecycleObserver {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var pool: SoundPool? = null
    private var sound = 0
    private var stream = 0
    private var ready = false
    private var active = false
    private var released = false
    private var pendingUntil: Long? = null

    override fun onStart(owner: LifecycleOwner) {
        if (released) return
        active = true
        if (pool != null) return
        val created = createPool()
        pool = created
        created.setOnLoadCompleteListener { loadedPool, sample, status ->
            if (released || loadedPool !== pool || sample != sound) return@setOnLoadCompleteListener
            ready = status == 0
            val deadline = pendingUntil
            pendingUntil = null
            if (ready && deadline != null && SystemClock.uptimeMillis() <= deadline) play()
        }
        sound = created.load(context, R.raw.witch_purr, 1)
    }

    fun play() {
        if (released || !active || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        ) {
            pendingUntil = null
            return
        }
        if (!ready) {
            pendingUntil = SystemClock.uptimeMillis() + 500
            return
        }
        pendingUntil = null
        stream = pool?.play(sound, 1f, 1f, 1, 0, 1f) ?: 0
    }

    override fun onStop(owner: LifecycleOwner) {
        active = false
        pendingUntil = null
        if (stream != 0) pool?.stop(stream)
        stream = 0
    }

    fun release() {
        released = true
        active = false
        pendingUntil = null
        pool?.setOnLoadCompleteListener(null)
        pool?.release()
        pool = null
    }
}
