package app.farvater.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import app.farvater.R
import app.farvater.vpn.VpnBus
import app.farvater.vpn.VpnWidget

enum class AppIcon(
    val alias: String,
    val title: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
    @DrawableRes val monochrome: Int,
    @DrawableRes val widget: Int,
) {
    CLASSIC(
        "MainActivity", "Классический",
        R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground,
        R.drawable.ic_launcher_monochrome, R.drawable.widget_on,
    ),
    NIGHT(
        "IconNight", "Ночь",
        R.drawable.ic_launcher_night_background, R.drawable.ic_launcher_night_foreground,
        R.drawable.ic_launcher_night_monochrome, R.drawable.widget_on_night,
    ),
    DAY(
        "IconDay", "День",
        R.drawable.ic_launcher_day_background, R.drawable.ic_launcher_day_foreground,
        R.drawable.ic_launcher_day_monochrome, R.drawable.widget_on_day,
    ),
    ORANGE(
        "IconOrange", "Оранжевый",
        R.drawable.ic_launcher_orange_background, R.drawable.ic_launcher_orange_foreground,
        R.drawable.ic_launcher_orange_monochrome, R.drawable.widget_on_orange,
    ),
    BLUE(
        "IconBlue", "Голубой",
        R.drawable.ic_launcher_blue_background, R.drawable.ic_launcher_blue_foreground,
        R.drawable.ic_launcher_blue_monochrome, R.drawable.widget_on_blue,
    ),
    NOTHING(
        "IconNothing", "Nothing",
        R.drawable.ic_launcher_nothing_background, R.drawable.ic_launcher_nothing_foreground,
        R.drawable.ic_launcher_nothing_monochrome, R.drawable.widget_on_nothing,
    ),
    SUMIE(
        "IconSumie", "Сумиэ",
        R.drawable.ic_launcher_sumie_background, R.drawable.ic_launcher_sumie_foreground,
        R.drawable.ic_launcher_sumie_monochrome, R.drawable.widget_on_sumie,
    ),
    LETTER(
        "IconLetter", "Буква F",
        R.drawable.ic_launcher_letter_background, R.drawable.ic_launcher_letter_foreground,
        R.drawable.ic_launcher_letter_monochrome, R.drawable.widget_on_letter,
    ),
    ORIGIN(
        "IconOrigin", "Origin",
        R.drawable.ic_launcher_origin_background, R.drawable.ic_launcher_origin_foreground,
        R.drawable.ic_launcher_origin_monochrome, R.drawable.widget_on_origin,
    ),
    HYPER(
        "IconHyper", "Hyper",
        R.drawable.ic_launcher_hyper_background, R.drawable.ic_launcher_hyper_foreground,
        R.drawable.ic_launcher_hyper_monochrome, R.drawable.widget_on_hyper,
    ),
    HARMONY(
        "IconHarmony", "Harmony",
        R.drawable.ic_launcher_harmony_background, R.drawable.ic_launcher_harmony_foreground,
        R.drawable.ic_launcher_harmony_monochrome, R.drawable.widget_on_harmony,
    ),
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
        VpnWidget.refresh(context, VpnBus.state.value)
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
