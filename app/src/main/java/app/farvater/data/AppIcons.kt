package app.farvater.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.farvater.R

enum class AppIcon(
    val alias: String,
    val title: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    CLASSIC("MainActivity", "Классический", R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground),
    NIGHT("IconNight", "Ночь", R.drawable.ic_launcher_night_background, R.drawable.ic_launcher_night_foreground),
    DAY("IconDay", "День", R.drawable.ic_launcher_day_background, R.drawable.ic_launcher_day_foreground),
    ORANGE("IconOrange", "Оранжевый", R.drawable.ic_launcher_orange_background, R.drawable.ic_launcher_orange_foreground),
    BLUE("IconBlue", "Голубой", R.drawable.ic_launcher_blue_background, R.drawable.ic_launcher_blue_foreground),
    NOTHING("IconNothing", "Nothing", R.drawable.ic_launcher_nothing_background, R.drawable.ic_launcher_nothing_foreground),
    SUMIE("IconSumie", "Сумиэ", R.drawable.ic_launcher_sumie_background, R.drawable.ic_launcher_sumie_foreground),
    LETTER("IconLetter", "Буква F", R.drawable.ic_launcher_letter_background, R.drawable.ic_launcher_letter_foreground),
}

object AppIcons {
    private const val PENDING = "pending_icon"

    fun current(context: Context): AppIcon =
        AppIcon.entries.firstOrNull { it != AppIcon.CLASSIC && enabled(context, it) } ?: AppIcon.CLASSIC

    fun selected(context: Context): AppIcon = pending(context) ?: current(context)

    fun choose(context: Context, icon: AppIcon) {
        prefs(context).edit().putString(PENDING, icon.name.takeIf { icon != current(context) }).apply()
    }

    fun applyPending(context: Context) {
        val icon = pending(context) ?: return
        prefs(context).edit().remove(PENDING).apply()
        if (icon == current(context)) return
        runCatching {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(component(context, icon), stateFor(icon, icon), PackageManager.DONT_KILL_APP)
            AppIcon.entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(component(context, it), stateFor(it, icon), PackageManager.DONT_KILL_APP)
            }
        }
    }

    private fun stateFor(alias: AppIcon, chosen: AppIcon): Int = when {
        alias == chosen && alias == AppIcon.CLASSIC -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        alias == chosen -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        alias == AppIcon.CLASSIC -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        chosen == AppIcon.CLASSIC -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun enabled(context: Context, icon: AppIcon): Boolean =
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.CLASSIC
            else -> false
        }

    private fun pending(context: Context): AppIcon? =
        prefs(context).getString(PENDING, null)?.let { name -> AppIcon.entries.firstOrNull { it.name == name } }

    private fun component(context: Context, icon: AppIcon) = ComponentName(context, "app.farvater.${icon.alias}")

    private fun prefs(context: Context) = context.getSharedPreferences("farvater", Context.MODE_PRIVATE)
}
