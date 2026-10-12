package app.farvater.vpn

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.farvater.R

class VpnWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids, VpnBus.state.value)
    }

    companion object {
        private const val OFF_TINT = 0xFF9AA0A6.toInt()

        fun refresh(context: Context, state: VpnState) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, state)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, state: VpnState) {
            val on = state is VpnState.Connected || state is VpnState.Connecting
            val toggle = PendingIntent.getActivity(
                context,
                0,
                Intent(context, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_vpn).apply {
                setInt(R.id.widget_button, "setBackgroundResource", if (on) R.drawable.widget_on else R.drawable.widget_off)
                setImageViewResource(R.id.widget_icon, if (on) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_monochrome)
                setInt(R.id.widget_icon, "setColorFilter", if (on) 0 else OFF_TINT)
                setContentDescription(
                    R.id.widget_button,
                    if (on) "Фарватер включён, нажмите, чтобы отключить" else "Фарватер выключен, нажмите, чтобы подключиться",
                )
                setOnClickPendingIntent(R.id.widget_button, toggle)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
