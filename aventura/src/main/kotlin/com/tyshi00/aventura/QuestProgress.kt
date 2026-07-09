package com.tyshi00.aventura

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields

/** XP rules, derived from the tier so the caller doesn't need to track it separately. */
object Xp {
    fun forKind(kind: String): Int = when (kind) {
        "DAILY" -> 10
        "WEEKLY" -> 25
        "MONTHLY" -> 75
        else -> 10
    }
}

data class Level(val number: Int, val name: String, val minXp: Int)

/** Dry, grounded ranks for getting further from the screen. */
object Levels {
    val all = listOf(
        Level(1, "Plugged in", 0),
        Level(2, "Looking up", 75),
        Level(3, "Out the door", 250),
        Level(4, "Out and about", 600),
        Level(5, "In the world", 1100),
        Level(6, "Wide awake", 1800),
        Level(7, "All here", 2800),
        Level(8, "Tuned in", 4200),
        Level(9, "Among people", 6000),
        Level(10, "In the mix", 8200),
        Level(11, "Involved", 11000),
        Level(12, "Reconnected", 14500),
    )

    fun forXp(xp: Int): Level = all.last { xp >= it.minXp }

    /** The level after this one, or null if already at the top. */
    fun next(level: Level): Level? = all.getOrNull(level.number)
}

data class Trophy(val id: String, val name: String, val desc: String)

data class ProgressStats(
    val total: Int,
    val totalXp: Int,
    val daily: Int,
    val weekly: Int,
    val monthly: Int,
    val streak: Int,
    val bestStreak: Int,
)

/** Day streaks, derived from the completion history. */
object Streak {

    private fun daysOf(history: List<CompletedQuestEntry>): Set<LocalDate> =
        history.mapTo(HashSet()) {
            Instant.ofEpochMilli(it.completedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    fun current(history: List<CompletedQuestEntry>, today: LocalDate = LocalDate.now()): Int {
        val days = daysOf(history)
        if (days.isEmpty()) return 0
        var day = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        return n
    }

    fun longest(history: List<CompletedQuestEntry>): Int {
        val days = daysOf(history).sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            run = if (days[i] == days[i - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Bonus XP added to each finished quest, by the streak it's part of. */
    fun bonusXp(streak: Int): Int = when {
        streak >= 30 -> 20
        streak >= 14 -> 15
        streak >= 7 -> 10
        streak >= 3 -> 5
        else -> 0
    }
}

fun List<CompletedQuestEntry>.stats(): ProgressStats = ProgressStats(
    total = size,
    totalXp = sumOf { it.xp },
    daily = count { it.kind == "DAILY" },
    weekly = count { it.kind == "WEEKLY" },
    monthly = count { it.kind == "MONTHLY" },
    streak = Streak.current(this),
    bestStreak = Streak.longest(this),
)

object Trophies {
    val all = listOf(
        Trophy("first", "First step", "Finish your first quest."),
        Trophy("ten", "Ten down", "Finish ten quests."),
        Trophy("fifty", "Fifty", "Finish fifty quests."),
        Trophy("hundred", "Hundred", "Finish a hundred quests."),
        Trophy("streak3", "Three days running", "Keep a three day streak."),
        Trophy("streak7", "A week unbroken", "Keep a seven day streak."),
        Trophy("streak30", "A month unbroken", "Keep a thirty day streak."),
        Trophy("day-all", "Clean sweep", "Finish all of a day's quests."),
        Trophy("weekend", "Weekender", "Finish every daily on a Saturday and the Sunday after."),
        Trophy("day-week", "Sweep the week", "Finish every daily, every day, for a week."),
        Trophy("day-month", "Sweep the month", "Finish every daily, every day, for a whole month."),
        Trophy("week-all", "Week cleared", "Finish all the weekly quests in a week."),
        Trophy("weekly10", "Week after week", "Finish ten weekly quests."),
        Trophy("week-month", "Month of weeks", "Clear the weekly quests four weeks running."),
        Trophy("monthly1", "Big one", "Finish a monthly quest."),
        Trophy("month-all", "Month cleared", "Finish all the monthly quests in a month."),
        Trophy("night-owl", "Night owl", "Finish a quest after midnight."),
        Trophy("early-bird", "Early bird", "Finish a quest before six in the morning."),
        Trophy("last-minute", "Last minute", "Finish a quest in the last hour before midnight."),
        Trophy("touched-grass", "Touched grass", "Finish an outdoor quest."),
        Trophy("overachiever", "Overachiever", "Finish five quests in a single day."),
        Trophy("comeback", "Comeback", "Lose a streak, then start another one."),
    )

    fun unlocked(history: List<CompletedQuestEntry>): Set<String> {
        val s = history.stats()
        val out = mutableSetOf<String>()
        if (s.total >= 1) out += "first"
        if (s.total >= 10) out += "ten"
        if (s.total >= 50) out += "fifty"
        if (s.total >= 100) out += "hundred"
        if (s.bestStreak >= 3) out += "streak3"
        if (s.bestStreak >= 7) out += "streak7"
        if (s.bestStreak >= 30) out += "streak30"
        if (s.weekly >= 10) out += "weekly10"
        if (s.monthly >= 1) out += "monthly1"

        // Per-period completeness. You can only complete a period's own selection, so a full
        // count of distinct completions for a period means the whole set was cleared.
        val counts = history.groupingBy {
            val p = it.completionKey.split("|")
            (p.getOrElse(0) { "" }) to (p.getOrElse(1) { "" })
        }.eachCount()

        val fullDays = counts.filter { it.key.first == "DAILY" && it.value >= QuestTier.DAILY.count }
            .mapNotNull { runCatching { LocalDate.parse(it.key.second) }.getOrNull() }.toSet()
        val fullWeeks = counts.filter { it.key.first == "WEEKLY" && it.value >= QuestTier.WEEKLY.count }
            .mapNotNull { weekMonday(it.key.second) }.toSet()
        val fullMonths = counts.filter { it.key.first == "MONTHLY" && it.value >= QuestTier.MONTHLY.count }
            .mapNotNull { runCatching { YearMonth.parse(it.key.second) }.getOrNull() }
            .map { it.year * 12 + (it.monthValue - 1) }.toSet()

        if (fullDays.isNotEmpty()) out += "day-all"
        if (fullDays.any { it.dayOfWeek == DayOfWeek.SATURDAY && it.plusDays(1) in fullDays }) out += "weekend"
        if (fullDays.any { d -> (1L..6L).all { d.plusDays(it) in fullDays } }) out += "day-week"
        if (fullDays.groupBy { YearMonth.from(it) }.any { (ym, _) ->
                (1..ym.lengthOfMonth()).all { LocalDate.of(ym.year, ym.monthValue, it) in fullDays }
            }) out += "day-month"

        if (fullWeeks.isNotEmpty()) out += "week-all"
        if (fullWeeks.any { m -> (1L..3L).all { m.plusWeeks(it) in fullWeeks } }) out += "week-month"

        if (fullMonths.isNotEmpty()) out += "month-all"

        // silly ones, from completion times and dates
        val zone = ZoneId.systemDefault()
        fun hourOf(e: CompletedQuestEntry) = Instant.ofEpochMilli(e.completedAtMillis).atZone(zone).hour
        fun dateOf(e: CompletedQuestEntry) = Instant.ofEpochMilli(e.completedAtMillis).atZone(zone).toLocalDate()
        if (history.any { hourOf(it) in 0..3 }) out += "night-owl"
        if (history.any { hourOf(it) in 4..5 }) out += "early-bird"
        if (history.any { hourOf(it) == 23 }) out += "last-minute"
        if (history.any { it.category == "Go" }) out += "touched-grass"
        if (history.groupingBy { dateOf(it) }.eachCount().any { it.value >= 5 }) out += "overachiever"
        // comeback: completion days form more than one separate run (a streak broke, then resumed)
        val days = history.map { dateOf(it) }.toSortedSet().toList()
        val runs = days.indices.count { i -> i == 0 || days[i] != days[i - 1].plusDays(1) }
        if (runs >= 2) out += "comeback"

        return out
    }

    /** Monday of the ISO week named "YYYY-Www", for stepping consecutive weeks. */
    private fun weekMonday(key: String): LocalDate? = runCatching {
        val (y, w) = key.split("-W")
        LocalDate.of(y.toInt(), 1, 4)
            .with(WeekFields.ISO.weekOfWeekBasedYear(), w.toLong())
            .with(WeekFields.ISO.dayOfWeek(), 1)
    }.getOrNull()
}
