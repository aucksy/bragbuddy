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
 * v2 adds `entries.anchorProject` (the folder-tap project anchor). v3 makes the `projects` unique
 * index composite `(name, goalArea)` so the same folder name can exist under different categories.
 * v4 adds `entries.audioPath` (the offline voice-note queue, Phase 7). All migrations keep existing
 * data. exportSchema stays off (no schemaLocation ksp arg wired).
 */
@Database(
    entities = [EntryEntity::class, ProjectEntity::class],
    version = 4,
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

        /**
         * v2→v3: replace the single-column unique index on `projects.name` with a composite unique
         * index on `(name, goalArea)`. Index names must match Room's auto-generated names exactly so
         * the schema-identity check passes. Existing rows all had globally-unique names, so the new
         * composite index can't collide.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_projects_name")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projects_name_goalArea ON projects (name, goalArea)")
            }
        }

        /** v3→v4: add the nullable `audioPath` column to `entries` (the offline voice-note queue). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN audioPath TEXT")
            }
        }
    }
}
