package com.tyshi00.aventura

/** The three quest cadences and how many quests each surfaces per period. */
enum class QuestTier(val label: String, val count: Int) {
    DAILY("Daily", 3),
    WEEKLY("Weekly", 4),
    MONTHLY("Monthly", 12),
}

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val category: String = "",
    /** Which tiers this quest is eligible to appear in. */
    val tiers: List<QuestTier> = emptyList(),
    /** Rough time cost in minutes; 0 means "open-ended". */
    val minutes: Int = 0,
)
