package app.farvater.data

import android.content.SharedPreferences
import android.util.Base64
import app.farvater.storage.ProfileCipher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal object ProfileStorage {
    val sensitive = setOf("settings", "user_sources", "last_node", "mode_nodes", "backup_nodes", "manual_nodes", "hwid")

    private inline fun <T> withProvider(action: () -> T): T = try {
        action()
    } catch (failure: java.security.ProviderException) {
        throw java.security.GeneralSecurityException("Key provider unavailable", failure)
    }

    fun sealedKey(key: String) = "sealed.v1.$key"

    fun put(editor: SharedPreferences.Editor, key: String, value: String?): SharedPreferences.Editor {
        if (key !in sensitive) return editor.putString(key, value)
        val protected = value?.let {
            Base64.encodeToString(withProvider { ProfileCipher.seal("pref/$key", it.toByteArray()) }, Base64.NO_WRAP)
        }
        return editor.putString(sealedKey(key), protected).remove(key)
    }

    fun get(prefs: SharedPreferences, key: String): String? {
        if (key !in sensitive) return prefs.getString(key, null)
        return synchronized(Prefs.selectionLock) {
            val encrypted = prefs.getString(sealedKey(key), null)
            if (encrypted != null) {
                val bytes = try {
                    Base64.decode(encrypted, Base64.NO_WRAP)
                } catch (error: IllegalArgumentException) {
                    throw java.security.GeneralSecurityException("Invalid protected profile encoding", error)
                }
                val value = withProvider { ProfileCipher.open("pref/$key", bytes) }.decodeToString()
                if (prefs.contains(key) && !prefs.edit().remove(key).commit()) throw IOException("Cannot remove legacy profile")
                value
            } else {
                val legacy = prefs.getString(key, null) ?: return@synchronized null
                if (!put(prefs.edit(), key, legacy).commit()) throw IOException("Cannot migrate profile")
                legacy
            }
        }
    }

    fun modified(file: File) = if (sealed(file).isFile) sealed(file).lastModified() else file.lastModified()

    private fun sealed(file: File) = File(file.parentFile, file.name + ".sealed")

    @Synchronized
    fun read(file: File): String? {
        val encrypted = sealed(file)
        if (encrypted.isFile) {
            val clear = withProvider { ProfileCipher.open("file/" + file.name, encrypted.readBytes()) }.decodeToString()
            Files.deleteIfExists(file.toPath())
            return clear
        }
        if (!file.exists()) return null
        val clear = file.readText()
        write(file, clear, file.lastModified())
        return clear
    }

    @Synchronized
    fun write(file: File, value: String, stamp: Long? = null) {
        val encrypted = withProvider { ProfileCipher.seal("file/" + file.name, value.toByteArray()) }
        val tmp = File.createTempFile("sealed-", ".tmp", file.parentFile)
        try {
            FileOutputStream(tmp).use { it.write(encrypted); it.fd.sync() }
            if (stamp != null && !tmp.setLastModified(stamp)) throw IOException("Cannot preserve cache timestamp")
            Files.move(tmp.toPath(), sealed(file).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            Files.deleteIfExists(file.toPath())
        } finally {
            tmp.delete()
        }
    }

    @Synchronized
    fun delete(file: File) {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(sealed(file).toPath())
    }
}
