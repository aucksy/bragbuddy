package com.bragbuddy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The local-first store. Room is the source of truth (Build Brief: "local data remains the source
 * of truth; Drive is redundancy"). The running rollup + summary tables are added in Phase 5.
 *
 * v2 adds `entries.anchorProject` (the folder-tap project anchor). [MIGRATION_1_2] is an additive
 * `ALTER TABLE`, so existing installs keep all their data. exportSchema stays off (no schemaLocation
 * ksp arg wired) — the migration is trivial and additive.
 */
@Database(
    entities = [EntryEntity::class, ProjectEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BragBuddyDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun projectDao(): ProjectDao

    companion object {
        const val NAME = "bragbuddy.db"

        /** v1→v2: add the nullable `anchorProject` column to `entries` (keeps all existing rows). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN anchorProject TEXT")
            }
        }
    }
}
