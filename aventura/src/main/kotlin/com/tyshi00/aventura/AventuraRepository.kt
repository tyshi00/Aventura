package com.tyshi00.aventura

import androidx.room.withTransaction

class AventuraRepository(private val db: AventuraDatabase) {

    companion object {
        const val PREF_INVERT = "invert_colors"
        const val PREF_SHOW_STREAKS = "show_streaks"
        const val PREF_SHOW_TROPHIES = "show_trophies"
        const val PREF_LAST_AUTO_BACKUP = "last_auto_backup_at"
        const val DB_NAME = "aventura.db"

        @Volatile private var INSTANCE: AventuraRepository? = null

        fun getInstance(factory: () -> AventuraDatabase): AventuraRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AventuraRepository(factory()).also { INSTANCE = it }
            }
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    suspend fun getInvertColors(): Boolean {
        return db.preferenceDao().get(PREF_INVERT)?.value == "true"
    }

    suspend fun setInvertColors(value: Boolean) {
        db.preferenceDao().set(PreferenceEntry(PREF_INVERT, value.toString()))
    }

    // Both default to on, so existing installs keep behaving the same until someone opts out.
    suspend fun getShowStreaks(): Boolean {
        return db.preferenceDao().get(PREF_SHOW_STREAKS)?.value != "false"
    }

    suspend fun setShowStreaks(value: Boolean) {
        db.preferenceDao().set(PreferenceEntry(PREF_SHOW_STREAKS, value.toString()))
    }

    suspend fun getShowTrophies(): Boolean {
        return db.preferenceDao().get(PREF_SHOW_TROPHIES)?.value != "false"
    }

    suspend fun setShowTrophies(value: Boolean) {
        db.preferenceDao().set(PreferenceEntry(PREF_SHOW_TROPHIES, value.toString()))
    }

    // ── Quest completion ─────────────────────────────────────────────────────

    suspend fun getHistory(): List<CompletedQuestEntry> = db.completedQuestDao().getAll()

    suspend fun isCompleted(completionKey: String): Boolean =
        db.completedQuestDao().getByCompletionKey(completionKey) != null

    /** Mark a quest done (appends a timestamped history entry) or undone (removes it). */
    suspend fun setCompleted(selected: SelectedQuest, done: Boolean) {
        val dao = db.completedQuestDao()
        if (done) {
            if (dao.getByCompletionKey(selected.completionKey) != null) return
            val kind = selected.completionKey.substringBefore("|")
            val base = Xp.forKind(kind)
            val provisional = CompletedQuestEntry(
                completionKey = selected.completionKey,
                questId = selected.quest.id,
                title = selected.quest.title,
                category = selected.quest.category,
                kind = kind,
                xp = base,
                completedAtMillis = System.currentTimeMillis(),
            )
            // Streak (now including today's quest) pays a small bonus, folded into the XP,
            // unless the person has turned streaks off entirely.
            val bonus = if (getShowStreaks()) {
                val streakSoFar = Streak.current(getHistory() + provisional)
                Streak.bonusXp(streakSoFar)
            } else {
                0
            }
            dao.insert(provisional.copy(xp = base + bonus))
        } else {
            dao.deleteByCompletionKey(selected.completionKey)
        }
    }

    suspend fun resetAll() {
        db.completedQuestDao().resetAll()
        db.preferenceDao().resetAll()
    }

    suspend fun getLastAutoBackupAt(): Long =
        db.preferenceDao().get(PREF_LAST_AUTO_BACKUP)?.value?.toLongOrNull() ?: 0L

    suspend fun setLastAutoBackupAt(value: Long) {
        db.preferenceDao().set(PreferenceEntry(PREF_LAST_AUTO_BACKUP, value.toString()))
    }

    /**
     * Saves the daily automatic backup if one is due. It goes to two places: a private copy inside the
     * app, and a dated copy in Documents/Aventura, which is the one that survives an uninstall. The
     * day counts as done if at least one of them was written and verified. A failed save is retried
     * the next time the app opens.
     */
    suspend fun autoBackupIfDue(
        store: BackupStore,
        folders: BackupFolders,
        entryCount: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!AutoBackupPolicy.isDue(getLastAutoBackupAt(), nowMillis, entryCount)) return false
        val data = exportBackup()
        val text = Backup.encode(data)
        val privateOk = store.writeAuto(text, data.entries.size)
        val publicOk = runCatching { folders.writePublicCopy(text, BackupKind.AUTO).ok }.getOrDefault(false)
        val saved = privateOk || publicOk
        if (saved) setLastAutoBackupAt(nowMillis)
        return saved
    }

    // ── Backup & restore ─────────────────────────────────────────────────────

    suspend fun exportBackup(): BackupData = BackupData(
        savedAtMillis = System.currentTimeMillis(),
        invertColors = getInvertColors(),
        showStreaks = getShowStreaks(),
        showTrophies = getShowTrophies(),
        entries = getHistory().map {
            BackupEntry(
                completionKey = it.completionKey,
                questId = it.questId,
                title = it.title,
                category = it.category,
                kind = it.kind,
                xp = it.xp,
                completedAtMillis = it.completedAtMillis,
            )
        },
    )

    /**
     * Replaces all current data with what's in the backup. Runs as a single transaction, so a
     * failure part way through leaves the existing data untouched instead of half restored.
     */
    suspend fun restoreBackup(data: BackupData) {
        db.withTransaction {
            db.completedQuestDao().resetAll()
            db.completedQuestDao().insertAll(
                data.entries.distinctBy { it.completionKey }.map {
                    CompletedQuestEntry(
                        id = 0, // let Room assign fresh row ids
                        completionKey = it.completionKey,
                        questId = it.questId,
                        title = it.title,
                        category = it.category,
                        kind = it.kind,
                        xp = it.xp,
                        completedAtMillis = it.completedAtMillis,
                    )
                },
            )
            setInvertColors(data.invertColors)
            setShowStreaks(data.showStreaks)
            setShowTrophies(data.showTrophies)
        }
    }
}
