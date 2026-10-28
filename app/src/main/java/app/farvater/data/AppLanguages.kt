package app.farvater.data

import android.app.Activity
import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import androidx.core.content.edit
import app.farvater.vpn.QsTileService
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnWidget
import java.util.Locale

object AppLanguages {
    val supported = listOf("ru", "en", "fa")
    private const val KEY = "app_language"
    private const val MIGRATED = "app_language_migrated"

    private fun prefs(context: Context) = context.getSharedPreferences("farvater", Context.MODE_PRIVATE)

    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(',')
    } else {
        prefs(context).getString(KEY, "").orEmpty()
    }

    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < 33 || prefs(context).getBoolean(MIGRATED, false)) return
        val manager = context.getSystemService(LocaleManager::class.java)
        val saved = prefs(context).getString(KEY, "").orEmpty()
        if (manager.applicationLocales.isEmpty && saved in supported) {
            manager.applicationLocales = LocaleList.forLanguageTags(saved)
        }
        prefs(context).edit { putBoolean(MIGRATED, true) }
    }

    fun wrap(context: Context): Context {
        val selected = selected(context)
        val system = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }
        val locale = if (selected.isNotEmpty()) Locale.forLanguageTag(selected) else {
            (0 until system.size()).map { system[it] }.firstOrNull { it.language in supported } ?: Locale.forLanguageTag("ru")
        }
        val effective = if (locale.language in supported) locale else Locale.forLanguageTag("ru")
        val locales = LocaleList(effective)
        if (context.resources.configuration.locales == locales) return context
        val config = Configuration(context.resources.configuration).apply {
            setLocales(locales)
            setLayoutDirection(effective)
        }
        return context.createConfigurationContext(config)
    }

    fun set(context: Context, language: String) {
        require(language.isEmpty() || language in supported)
        if (selected(context) == language) return
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        } else {
            prefs(context).edit { putString(KEY, language) }
            var current: Context = context
            while (current is ContextWrapper && current !is Activity) current = current.baseContext
            (current as? Activity)?.recreate()
        }
        refresh(context)
    }

    fun refresh(context: Context) {
        (context.applicationContext as? app.farvater.App)?.refreshChannels()
        VpnWidget.refresh(context, VpnBus.state.value)
        TileService.requestListeningState(context, ComponentName(context, QsTileService::class.java))
    }
}

fun Context.localizedString(@StringRes id: Int, vararg args: Any): String =
    AppLanguages.wrap(this).getString(id, *args)
