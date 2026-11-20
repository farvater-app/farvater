package app.farvater.engine

internal class BackupCooldowns {
    private data class Failure(val attempts: Int, val at: Long, val until: Long)
    private val failures = LinkedHashMap<String, Failure>()

    @Synchronized
    fun failed(id: String, now: Long) {
        prune(now)
        val attempts = ((failures[id]?.attempts ?: 0) + 1).coerceAtMost(3)
        failures[id] = Failure(attempts, now, now + (30_000L shl (attempts - 1)))
        while (failures.size > 64) failures.remove(failures.keys.first())
    }

    @Synchronized
    fun excluded(now: Long): Set<String> {
        prune(now)
        return failures.filterValues { now < it.until }.keys.toSet()
    }

    @Synchronized
    fun clear() = failures.clear()

    private fun prune(now: Long) {
        failures.entries.removeAll { now - it.value.at !in 0 until 600_000L }
    }
}
