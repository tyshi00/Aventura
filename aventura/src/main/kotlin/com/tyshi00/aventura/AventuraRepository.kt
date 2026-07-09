package com.tyshi00.aventura

class AventuraRepository(private val db: AventuraDatabase) {

    companion object {
        const val PREF_INVERT = "invert_colors"
        const val PREF_SHOW_STREAKS = "show_streaks"
        const val PREF_SHOW_TROPHIES = "show_trophies"
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
}
