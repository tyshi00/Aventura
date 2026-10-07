package com.tyshi00.aventura

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One completed quest, stripped of its internal Room row id, which is just an implementation detail. */
@Serializable
data class BackupEntry(
    val completionKey: String,
    val questId: String,
    val title: String,
    val category: String,
    val kind: String,
    val xp: Int,
    val completedAtMillis: Long,
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val savedAtMillis: Long = 0L,
    val invertColors: Boolean,
    val showStreaks: Boolean,
    val showTrophies: Boolean,
    val entries: List<BackupEntry>,
)

object Backup {
    const val FILE_NAME = "aventura-backup.json"
    const val AUTO_FILE_NAME = "aventura-auto-backup.json"

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

    /** Returns null if the text isn't a valid Aventura backup, so junk never reaches the database. */
    fun decode(text: String): BackupData? {
        if (text.length > BackupValidation.MAX_FILE_CHARS) return null
        val data = runCatching { json.decodeFromString(BackupData.serializer(), text.trim()) }
            .getOrNull() ?: return null
        return data.takeIf { BackupValidation.isValid(it) }
    }
}

/** When the daily automatic backup should run. Pure logic, so it can be tested on its own. */
object AutoBackupPolicy {
    const val INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Never due when there's nothing to back up, so an accidental reset can't cause the next
     * automatic backup to overwrite the last good one with an empty copy. Due when no backup has
     * been made yet, when the clock has gone backwards, or when a full day has passed.
     */
    fun isDue(lastAtMillis: Long, nowMillis: Long, entryCount: Int): Boolean {
        if (entryCount <= 0) return false
        if (lastAtMillis <= 0L || nowMillis < lastAtMillis) return true
        return nowMillis - lastAtMillis >= INTERVAL_MILLIS
    }
}
