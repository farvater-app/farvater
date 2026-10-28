package app.farvater.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.farvater.R
import app.farvater.data.DayTraffic
import app.farvater.data.TrafficHistory
import app.farvater.ui.formatBytes
import app.farvater.ui.ios.IosDivider
import app.farvater.ui.ios.IosRow
import app.farvater.ui.ios.IosSection
import app.farvater.ui.ios.IosSegmented
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

@Composable
fun TrafficPage(history: List<DayTraffic>, today: LocalDate = LocalDate.now()) {
    var period by rememberSaveable { mutableIntStateOf(0) }
    val c = Ios.colors
    val days = TrafficHistory.days(history, if (period == 0) 7 else 30, today.toEpochDay())
    val total = days.fold(0L) { sum, day -> sum + day.bytes.coerceIn(0, Long.MAX_VALUE - sum) }
    val maximum = days.maxOf { it.bytes }.coerceAtLeast(1)
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale).withDecimalStyle(DecimalStyle.of(locale))
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        IosSegmented(listOf(stringResource(R.string.traffic_week), stringResource(R.string.traffic_month)), period, { period = it })
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.traffic_total), style = IosType.subheadline, color = c.secondaryLabel)
        Text(formatBytes(total), style = IosType.title1, color = c.label)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(128.dp)) {
            val slot = size.width / days.size
            days.forEachIndexed { index, day ->
                val height = (day.bytes.toDouble() / maximum * size.height).toFloat()
                val x = if (rtl) days.lastIndex - index else index
                drawRect(c.tint, Offset((x + 0.15f) * slot, size.height - height), Size(slot * 0.7f, height))
            }
            drawLine(c.separator, Offset(0f, size.height), Offset(size.width, size.height))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalDate.ofEpochDay(days.first().day).format(formatter), style = IosType.footnote, color = c.secondaryLabel)
            Text(today.format(formatter), style = IosType.footnote, color = c.secondaryLabel)
        }
    }
    IosSection(footer = stringResource(R.string.traffic_privacy)) {
        days.asReversed().forEachIndexed { index, day ->
            IosRow(title = LocalDate.ofEpochDay(day.day).format(formatter), value = formatBytes(day.bytes))
            if (index != days.lastIndex) IosDivider()
        }
    }
}
