package app.farvater

import app.farvater.data.DayTraffic
import app.farvater.vpn.SessionTraffic
import org.junit.Assert.*
import org.junit.Test

class SessionTrafficTest {
    @Test fun separatesDaysMeasuresElapsedTimeAndFlushesFinalBytesOnce() {
        val saves = mutableListOf<DayTraffic>()
        var day = 10L
        var time = 0L
        var sent = 500L
        var received = 1000L
        val session = SessionTraffic({ longArrayOf(0, sent, 0, received) }, { day }, { time },
            { DayTraffic(it, if (it == 10L) 20 else 0) }, saves::add)
        session.sample(true, false)
        sent += 250
        received += 500
        time += 2500
        val tick = session.sample(true, false)!!
        assertEquals(100L, tick.upBps)
        assertEquals(200L, tick.downBps)
        assertEquals(770L, tick.today)
        assertTrue(saves.isEmpty())
        day++
        received += 17
        session.screenOff()
        assertEquals(0L, session.sample(false, false)!!.downBps)
        sent += 3
        session.close()
        session.close()
        assertNull(session.sample(true, true))
        assertEquals(listOf(DayTraffic(10, 770), DayTraffic(11, 20)), saves)
    }
}
