package com.tyshi00.aventura

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import kotlin.random.Random

/** A quest chosen for the current period, plus the stable key used to track completion. */
data class SelectedQuest(
    val quest: Quest,
    val completionKey: String,
)

/**
 * Deterministically selects the active quest set for each tier from the bundled [QuestPool].
 *
 * Selection is seeded by the period key (today's date / ISO week / month) so the same quests
 * stay on screen for the whole period and then rotate automatically when it rolls over — no
 * server, no state beyond the current date, and every install picks the same quests for the
 * same period.
 */
object QuestSelector {

    private val allQuests: List<Quest> = QuestPool.all

    fun periodKey(tier: QuestTier, today: LocalDate = LocalDate.now()): String = when (tier) {
        QuestTier.DAILY -> today.toString()
        QuestTier.WEEKLY -> {
            val week = today.get(WeekFields.ISO.weekOfWeekBasedYear())
            val year = today.get(WeekFields.ISO.weekBasedYear())
            "%d-W%02d".format(year, week)
        }
        QuestTier.MONTHLY -> YearMonth.from(today).toString()
    }

    fun selectionFor(tier: QuestTier, today: LocalDate = LocalDate.now()): List<SelectedQuest> {
        val period = periodKey(tier, today)
        val eligible = allQuests
            .filter { tier in it.tiers }
            .sortedBy { it.id } // stable base order before seeded shuffle
        if (eligible.isEmpty()) return emptyList()

        // One fixed running order, shared by every install (seed depends only on the tier, not
        // the period). We then walk a sliding window of `count` quests forward by one period at
        // a time, so each period shows the next batch and nothing repeats until the pool wraps.
        val ordered = eligible.shuffled(Random(stableSeed("ORDER|$tier")))
        val count = tier.count
        val start = periodIndex(tier, today) * count
        val chosen = (0 until count).map { ordered[((start + it) % ordered.size).toInt()] }
        return chosen.map { SelectedQuest(it, completionKey(tier, period, it.id)) }
    }

    /** Monotonic period number used to slide the selection window forward one period at a time. */
    private fun periodIndex(tier: QuestTier, today: LocalDate): Long = when (tier) {
        QuestTier.DAILY -> today.toEpochDay()
        QuestTier.WEEKLY -> {
            val week = today.get(WeekFields.ISO.weekOfWeekBasedYear())
            val year = today.get(WeekFields.ISO.weekBasedYear())
            year.toLong() * 53 + week
        }
        QuestTier.MONTHLY -> today.year.toLong() * 12 + (today.monthValue - 1)
    }

    fun completionKey(tier: QuestTier, period: String, questId: String): String =
        "$tier|$period|$questId"

    /**
     * Deterministic 64-bit hash (FNV-1a) of the period key. Platform-independent, so every
     * install computes the same seed for the same day/week/month and therefore picks the same
     * quests without any server.
     */
    private fun stableSeed(key: String): Long {
        var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
        for (ch in key) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L // FNV-1a 64-bit prime
        }
        return hash
    }
}
