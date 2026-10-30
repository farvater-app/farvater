package app.farvater

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.AppLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class BackupLanguageTest {
    @Test
    fun acknowledgingRestoredLanguageWritesOffMainThread() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = app.getSharedPreferences("farvater", 0)
        store.edit().clear().putString("backup_language", AppLanguages.selected(app)).commit()
        val caller = Thread.currentThread()
        val writer = AtomicReference<Thread>()
        val preferences = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            if (method.name == "edit") {
                val editor = store.edit()
                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, operation, values ->
                    if (operation.name == "commit") writer.set(Thread.currentThread())
                    val result = operation.invoke(editor, *(values ?: emptyArray()))
                    if (result === editor) proxy else result
                }
            } else method.invoke(store, *(args ?: emptyArray()))
        } as SharedPreferences
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try { runBlocking { AppLanguages.applyBackupLanguage(context) } }
        finally { Dispatchers.resetMain() }
        assertNotNull(writer.get())
        assertNotSame(caller, writer.get())
        assertFalse(store.contains("backup_language"))
    }
}
