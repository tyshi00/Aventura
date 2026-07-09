package com.thelightphone.tracker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

// ── Entities ─────────────────────────────────────────────────────────────────

@Entity(tableName = "water_entries")
data class WaterEntry(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val totalMl: Double = 0.0,
)

@Entity(tableName = "step_entries")
data class StepEntry(
    @PrimaryKey val date: String,
    val totalSteps: Int = 0,
)

/**
 * Sleep is stored as discrete sessions (bedtime → wake time) rather than a
 * single daily total, since a night's sleep almost always spans two
 * calendar dates. Multiple sessions per night are allowed (e.g. a nap),
 * same reasoning as Cycle/Mood.
 */
@Entity(tableName = "sleep_entries")
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bedDate: String, // YYYY-MM-DD — the date bedtime was on
    val bedTimeMinutes: Int, // minutes since midnight, 0-1439
    val wakeDate: String, // YYYY-MM-DD — inferred: bedDate, or bedDate+1 if wake crossed midnight
    val wakeTimeMinutes: Int, // minutes since midnight, 0-1439
    val durationMinutes: Int, // precomputed so averages don't need to recompute this every time
)

@Entity(tableName = "cycle_entries")
data class CycleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: String, // YYYY-MM-DD
    val endDate: String? = null, // null while the cycle is still ongoing
    val flow: String? = null, // FlowLevel.name
    val moods: String? = null, // comma-joined Mood.name values, up to 5 — only used when the standalone Mood tracker is off
    val energy: String? = null, // EnergyLevel.name
)

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey val date: String, // YYYY-MM-DD — one reading per day, like water/steps/sleep
    val weightKg: Double = 0.0,
)

/** Singleton row (id is always 0) holding the one-time starting weight reference point. */
@Entity(tableName = "starting_weight")
data class StartingWeightEntry(
    @PrimaryKey val id: Int = 0,
    val weightKg: Double,
    val date: String,
)

/** Multiple entries per day are allowed, like cycles — mood can shift through the day. */
@Entity(tableName = "mood_entries")
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val moods: String, // comma-joined Mood.name values, up to 5
    val notes: String? = null,
)

@Entity(tableName = "preferences")
data class PreferenceEntry(
    @PrimaryKey val key: String,
    val value: String,
)

// ── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_entries WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): WaterEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WaterEntry)

    @Query("SELECT * FROM water_entries WHERE date >= :from ORDER BY date DESC")
    suspend fun getFrom(from: String): List<WaterEntry>

    @Query("SELECT * FROM water_entries WHERE date >= :from AND date < :to ORDER BY date DESC")
    suspend fun getBetween(from: String, to: String): List<WaterEntry>

    @Query("UPDATE water_entries SET totalMl = 0")
    suspend fun resetAll()
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step_entries WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): StepEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StepEntry)

    @Query("SELECT * FROM step_entries WHERE date >= :from ORDER BY date DESC")
    suspend fun getFrom(from: String): List<StepEntry>

    @Query("SELECT * FROM step_entries WHERE date >= :from AND date < :to ORDER BY date DESC")
    suspend fun getBetween(from: String, to: String): List<StepEntry>

    @Query("UPDATE step_entries SET totalSteps = 0")
    suspend fun resetAll()
}

@Dao
interface SleepDao {
    @Insert
    suspend fun insert(entry: SleepEntry): Long

    @Query("SELECT * FROM sleep_entries ORDER BY bedDate DESC, wakeTimeMinutes DESC, id DESC LIMIT 1")
    suspend fun getMostRecent(): SleepEntry?

    @Query("SELECT * FROM sleep_entries WHERE bedDate >= :from ORDER BY bedDate DESC")
    suspend fun getFrom(from: String): List<SleepEntry>

    @Query("SELECT * FROM sleep_entries WHERE bedDate >= :from AND bedDate < :to ORDER BY bedDate DESC")
    suspend fun getBetween(from: String, to: String): List<SleepEntry>

    @Query("SELECT * FROM sleep_entries ORDER BY bedDate DESC, wakeTimeMinutes DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SleepEntry>

    @Query("DELETE FROM sleep_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sleep_entries")
    suspend fun resetAll()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE key = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entry: PreferenceEntry)
}

@Dao
interface CycleDao {
    @Insert
    suspend fun insert(entry: CycleEntry): Long

    @Update
    suspend fun update(entry: CycleEntry)

    @Query("SELECT * FROM cycle_entries ORDER BY startDate DESC LIMIT 1")
    suspend fun getMostRecent(): CycleEntry?

    @Query("SELECT * FROM cycle_entries ORDER BY startDate DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<CycleEntry>

    @Query("DELETE FROM cycle_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cycle_entries")
    suspend fun resetAll()
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_entries WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): WeightEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecent(): WeightEntry?

    @Query("SELECT * FROM weight_entries ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<WeightEntry>

    @Query("DELETE FROM weight_entries WHERE date = :date")
    suspend fun deleteForDate(date: String)

    @Query("DELETE FROM weight_entries")
    suspend fun resetAll()
}

@Dao
interface StartingWeightDao {
    @Query("SELECT * FROM starting_weight WHERE id = 0 LIMIT 1")
    suspend fun get(): StartingWeightEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entry: StartingWeightEntry)

    @Query("DELETE FROM starting_weight")
    suspend fun reset()
}

@Dao
interface MoodDao {
    @Insert
    suspend fun insert(entry: MoodEntry): Long

    @Query("SELECT * FROM mood_entries ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun getMostRecent(): MoodEntry?

    @Query("SELECT * FROM mood_entries WHERE date = :date ORDER BY id DESC LIMIT 1")
    suspend fun getMostRecentForDate(date: String): MoodEntry?

    @Query("SELECT * FROM mood_entries ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MoodEntry>

    @Query("SELECT COUNT(*) FROM mood_entries WHERE date >= :from AND date <= :to")
    suspend fun countInRange(from: String, to: String): Int

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_entries")
    suspend fun resetAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [
        WaterEntry::class, StepEntry::class, SleepEntry::class, CycleEntry::class,
        WeightEntry::class, StartingWeightEntry::class, MoodEntry::class, PreferenceEntry::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun waterDao(): WaterDao
    abstract fun stepDao(): StepDao
    abstract fun sleepDao(): SleepDao
    abstract fun cycleDao(): CycleDao
    abstract fun weightDao(): WeightDao
    abstract fun startingWeightDao(): StartingWeightDao
    abstract fun moodDao(): MoodDao
    abstract fun preferenceDao(): PreferenceDao
}
