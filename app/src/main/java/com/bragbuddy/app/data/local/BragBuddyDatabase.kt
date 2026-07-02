package com.bragbuddy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The local-first store. Room is the source of truth (Build Brief: "local data remains the source
 * of truth; Drive is redundancy"). The running rollup + summary tables are added in Phase 5.
 *
 * exportSchema is off while we are at v1 with no migrations. It will be turned on (with a committed
 * schemas/ dir) the moment the first migration is introduced, so migration tests have a baseline.
 */
@Database(
    entities = [EntryEntity::class, ProjectEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BragBuddyDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun projectDao(): ProjectDao

    companion object {
        const val NAME = "bragbuddy.db"
    }
}
