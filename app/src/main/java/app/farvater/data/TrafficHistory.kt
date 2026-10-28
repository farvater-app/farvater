package app.farvater.data

object TrafficHistory {
    const val RETENTION_DAYS = 60

    fun merge(history: List<DayTraffic>, reading: DayTraffic, today: Long): List<DayTraffic> =
        (history + reading)
            .filter { it.day >= today - RETENTION_DAYS + 1 && it.bytes >= 0 }
            .groupBy { it.day }
            .map { (day, readings) -> DayTraffic(day, readings.maxOf { it.bytes }) }
            .sortedBy { it.day }
            .takeLast(RETENTION_DAYS)

    fun days(history: List<DayTraffic>, count: Int, today: Long): List<DayTraffic> {
        require(count in 1..RETENTION_DAYS)
        val byDay = history.associateBy { it.day }
        return ((today - count + 1)..today).map { byDay[it] ?: DayTraffic(it, 0) }
    }
}
