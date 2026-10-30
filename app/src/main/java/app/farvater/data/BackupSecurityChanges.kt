package app.farvater.data

data class BackupSecurityChanges(
    val addedBypassApps: Set<String> = emptySet(),
    val removedBypassApps: Set<String> = emptySet(),
    val safeMode: Boolean? = null,
    val encryptedDns: Boolean? = null,
    val autoUpdates: Boolean? = null,
    val sendHwid: Boolean? = null,
    val directRuServices: Boolean? = null,
) {
    val isEmpty: Boolean get() = addedBypassApps.isEmpty() && removedBypassApps.isEmpty() &&
        listOf(safeMode, encryptedDns, autoUpdates, sendHwid, directRuServices).all { it == null }
}
