package com.thelightphone.sdk

import androidx.room.Room
import androidx.room.RoomDatabase

fun <T : RoomDatabase> SealedLightContext.buildDatabase(dbClass: Class<T>, dbName: String?): T {
    return Room.databaseBuilder(androidContext.applicationContext, dbClass, dbName)
        // No hand-written migrations exist yet for tools built on this SDK, so
        // a schema version bump (e.g. adding a table) would otherwise crash
        // existing installs with an IllegalStateException. Falling back to a
        // destructive migration means existing local data is wiped when the
        // schema changes, rather than the app crashing outright.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}