package app.farvater

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.farvater.data.DayTraffic
import app.farvater.data.Prefs
import app.farvater.data.TrafficHistory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ru", application = Application::class)
class TrafficHistoryTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val today = LocalDate.now().toEpochDay()

    @Before fun clear() {
        app.getSharedPreferences("farvater", 0).edit().clear().commit()
    }

    @Test fun oldTodayCounterMigratesWithoutLosingBytes() {
        app.getSharedPreferences("farvater", 0).edit()
            .putString("day_traffic", "{\"day\":$today,\"bytes\":123456}").commit()
        assertEquals(listOf(DayTraffic(today, 123456)), Prefs(app).trafficHistory)
        assertEquals(listOf(DayTraffic(today, 123456)), Prefs(app).trafficHistory)
    }

    @Test fun readingHistoryRemovesExpiredLegacyCounterFromStorage() {
        val sp = app.getSharedPreferences("farvater", 0)
        val old = "{\"day\":${today - 61},\"bytes\":123456}"
        sp.edit().putString("day_traffic", old).putString("traffic_history", "[$old]").commit()
        assertTrue(Prefs(app).trafficHistory.isEmpty())
        assertFalse(sp.contains("day_traffic"))
        assertEquals("[]", sp.getString("traffic_history", null))
    }

    @Test fun snapshotsDoNotDoubleCountAndSurviveRestart() {
        val prefs = Prefs(app)
        prefs.dayTraffic = DayTraffic(today - 1, 100)
        prefs.dayTraffic = DayTraffic(today - 1, 250)
        prefs.dayTraffic = DayTraffic(today, 500)
        prefs.dayTraffic = DayTraffic(today, 500)
        prefs.dayTraffic = DayTraffic(today, 200)
        assertEquals(listOf(DayTraffic(today - 1, 250), DayTraffic(today, 500)), Prefs(app).trafficHistory)
        assertEquals(DayTraffic(today, 500), Prefs(app).dayTraffic)
    }

    @Test fun retainedDataNeverExceedsSixtyCalendarDays() {
        val prefs = Prefs(app)
        (89 downTo 0).forEach { prefs.dayTraffic = DayTraffic(today - it, 100) }
        val history = Prefs(app).trafficHistory
        assertEquals(60, history.size)
        assertEquals(today - 59, history.first().day)
        assertEquals(today, history.last().day)
        val raw = app.getSharedPreferences("farvater", 0).getString("traffic_history", "")!!
        assertEquals(60, Regex("\\\"day\\\"").findAll(raw).count())
    }

    @Test fun returningToEarlierDateKeepsTheAlreadyCountedNextDay() {
        var history = TrafficHistory.merge(emptyList(), DayTraffic(today, 100), today)
        history = TrafficHistory.merge(history, DayTraffic(today + 1, 20), today + 1)
        history = TrafficHistory.merge(history, DayTraffic(today, 110), today)
        assertEquals(listOf(DayTraffic(today, 110), DayTraffic(today + 1, 20)), history)
    }

    @Test fun resumedDayContinuesItsSavedCounter() {
        val prefs = Prefs(app)
        prefs.dayTraffic = DayTraffic(today - 1, 100)
        prefs.dayTraffic = DayTraffic(today, 20)
        val resumed = prefs.trafficForDay(today - 1)
        prefs.dayTraffic = resumed.copy(bytes = resumed.bytes + 10)
        val restarted = Prefs(app)
        assertEquals(DayTraffic(today - 1, 110), restarted.trafficForDay(today - 1))
        assertEquals(DayTraffic(today, 20), restarted.trafficForDay(today))
    }

    @Test fun missingDaysAppearAsZeroAndFutureReadingsAreExcluded() {
        val history = TrafficHistory.merge(listOf(DayTraffic(today - 3, 20), DayTraffic(today + 1, 99)), DayTraffic(today, 5), today)
        val week = TrafficHistory.days(history, 7, today)
        assertEquals(7, week.size)
        assertEquals(25L, week.sumOf { it.bytes })
        assertEquals(0L, week[0].bytes)
        assertEquals(20L, week[3].bytes)
        assertEquals(30, TrafficHistory.days(history, 30, today).size)
    }
}
