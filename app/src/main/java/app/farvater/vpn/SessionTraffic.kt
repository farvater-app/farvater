package app.farvater.vpn

import app.farvater.data.DayTraffic

internal class SessionTraffic(
    private val counters: () -> LongArray?,
    private val today: () -> Long,
    private val nowMillis: () -> Long,
    private val loadDay: (Long) -> DayTraffic,
    private val saveDay: (DayTraffic) -> Unit,
) {
    private var day = loadDay(today())
    private var previous = counters()?.takeIf { it.size >= 4 }
    private var up = 0L
    private var down = 0L
    private var lastVisibleAt: Long? = null
    private var dirty = false
    private var closed = false

    fun screenOff() { lastVisibleAt = null }

    fun sample(visible: Boolean, flush: Boolean): Traffic? {
        if (closed) return null
        val current = counters()?.takeIf { it.size >= 4 }
        if (current == null) {
            if (flush) persist()
            return null
        }
        val old = previous
        previous = current
        val sent = old?.let { (current[1] - it[1]).coerceAtLeast(0) } ?: 0L
        val received = old?.let { (current[3] - it[3]).coerceAtLeast(0) } ?: 0L
        up += sent
        down += received
        val date = today()
        if (date != day.day) {
            persist()
            day = loadDay(date)
        }
        if (sent != 0L || received != 0L) {
            day = day.copy(bytes = day.bytes + sent + received)
            dirty = true
        }
        val now = nowMillis()
        val elapsed = lastVisibleAt?.let { (now - it).takeIf { age -> age > 0 } }
        lastVisibleAt = if (visible) now else null
        if (flush) persist()
        return Traffic(if (visible && elapsed != null) sent * 1000 / elapsed else 0,
            if (visible && elapsed != null) received * 1000 / elapsed else 0, up, down, day.bytes)
    }

    fun close() {
        if (closed) return
        sample(visible = false, flush = true)
        closed = true
    }

    private fun persist() {
        if (!dirty) return
        saveDay(day)
        dirty = false
    }
}
