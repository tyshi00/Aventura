package com.tyshi00.aventura

/**
 * What a backup has to look like before it is allowed anywhere near the database. Besides being the
 * right shape, every value has to sit inside limits that real data stays far below. Without limits,
 * a corrupted, hand-edited, or planted backup could pass the shape check and still crash the app on
 * every launch, for example two entries of 2,000,000,000 XP overflow the total to a negative number.
 *
 * Pure logic with no Android or serialization dependency, so it can be tested on its own.
 */
object BackupValidation {
    const val CURRENT_VERSION = 1

    /** A year of perfect use is about 1,450 entries, so this is roughly ten years of finishing everything. */
    const val MAX_ENTRIES = 15_000

    /**
     * The most a real entry can earn is 95: a monthly quest is worth 75, plus a streak bonus of at most
     * 20. If the XP rules ever change, raise this to match.
     */
    const val MAX_ENTRY_XP = 500

    /** Larger than any real backup by a wide margin. Checked before the text is parsed. */
    const val MAX_FILE_CHARS = 8_000_000

    private const val MAX_KEY_CHARS = 200
    private const val MAX_ID_CHARS = 100
    private const val MAX_TITLE_CHARS = 200
    private const val MAX_CATEGORY_CHARS = 40
    private const val YEAR_2100_MILLIS = 4_102_444_800_000L

    private val kinds = setOf("DAILY", "WEEKLY", "MONTHLY")

    fun isValid(data: BackupData): Boolean =
        data.version in 1..CURRENT_VERSION &&
            data.savedAtMillis in 0L..YEAR_2100_MILLIS &&
            data.entries.size <= MAX_ENTRIES &&
            data.entries.all { isValid(it) }

    /**
     * Streaks and trophies are derived by parsing each entry's completionKey ("KIND|period|questId")
     * and its kind, so entries that don't line up would silently corrupt those stats. Reject them.
     */
    private fun isValid(entry: BackupEntry): Boolean =
        entry.kind in kinds &&
            entry.completionKey.startsWith(entry.kind + "|") &&
            entry.completionKey.length <= MAX_KEY_CHARS &&
            entry.questId.length <= MAX_ID_CHARS &&
            entry.title.length <= MAX_TITLE_CHARS &&
            entry.category.length <= MAX_CATEGORY_CHARS &&
            entry.xp in 0..MAX_ENTRY_XP &&
            entry.completedAtMillis in 1L..YEAR_2100_MILLIS
}
