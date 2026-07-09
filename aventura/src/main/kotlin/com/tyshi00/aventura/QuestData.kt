package com.tyshi00.aventura

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

// ── Entities ─────────────────────────────────────────────────────────────────

/** One finished quest, kept in the history log — the source of truth for checkmarks and XP. */
@Entity(tableName = "completed_quests")
data class CompletedQuestEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completionKey: String, // e.g. "DAILY|2026-07-08|walk-block" — unique per quest per period
    val questId: String,
    val title: String,
    val category: String,
    val kind: String, // DAILY / WEEKLY / MONTHLY
    val xp: Int,
    val completedAtMillis: Long,
)

@Entity(tableName = "preferences")
data class PreferenceEntry(
    @PrimaryKey val key: String,
    val value: String,
)

// ── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface CompletedQuestDao {
    @Query("SELECT * FROM completed_quests ORDER BY completedAtMillis ASC")
    suspend fun getAll(): List<CompletedQuestEntry>

    @Query("SELECT * FROM completed_quests WHERE completionKey = :completionKey LIMIT 1")
    suspend fun getByCompletionKey(completionKey: String): CompletedQuestEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CompletedQuestEntry)

    @Query("DELETE FROM completed_quests WHERE completionKey = :completionKey")
    suspend fun deleteByCompletionKey(completionKey: String)

    @Query("DELETE FROM completed_quests")
    suspend fun resetAll()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE key = :key LIMIT 1")
    suspend fun get(key: String): PreferenceEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entry: PreferenceEntry)

    @Query("DELETE FROM preferences")
    suspend fun resetAll()
}

// ── Database ──────────────────────────────────────────────────────────────────

@Database(
    entities = [CompletedQuestEntry::class, PreferenceEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AventuraDatabase : RoomDatabase() {
    abstract fun completedQuestDao(): CompletedQuestDao
    abstract fun preferenceDao(): PreferenceDao
}
