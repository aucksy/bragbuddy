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
 * v4 adds `entries.audioPath` (the offline voice-note queue, Phase 7). v5 adds `entries.imagePath`
 * (the offline image-scan queue, M2). v6 adds `entries.anchorGoalArea` (the manual category anchor,
 * v0.31.0 — so a user's correction survives a re-file). v7 adds `entries.originalTranscript` (the
 * user's own words, preserved across an edit — v0.32.0). v8 adds the `deliverables` table plus
 * `entries.deliverable` / `entries.anchorDeliverable` (the third level — v0.33.0). All migrations keep
 * existing data. exportSchema stays off (no schemaLocation ksp arg wired).
 */
@Database(
    entities = [EntryEntity::class, ProjectEntity::class, DeliverableEntity::class],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BragBuddyDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun projectDao(): ProjectDao
    abstract fun deliverableDao(): DeliverableDao

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

        /** v4→v5: add the nullable `imagePath` column to `entries` (the offline image-scan queue). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN imagePath TEXT")
            }
        }

        /**
         * v5→v6: add the nullable `anchorGoalArea` column to `entries` — the manual category anchor.
         * Existing rows migrate to NULL, which is exactly right: nobody's placement was ever pinned
         * before, so nothing is retroactively claimed as a user decision. Anchors accrue as the user
         * corrects entries from here on.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN anchorGoalArea TEXT")
            }
        }

        /**
         * v6→v7: add the nullable `originalTranscript` column to `entries` — the user's own words,
         * preserved across an edit. Existing rows migrate to NULL, which reads as "never edited", so
         * their `rawTranscript` IS still the original. That is exactly right: a row edited BEFORE this
         * version already lost its original (the bug this column fixes), and there is nothing to
         * recover — claiming otherwise would be a lie about what the user said.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN originalTranscript TEXT")
            }
        }

        /**
         * v7→v8: the **third level** (Category → Project → Deliverable → entries, v0.33.0). Adds the
         * `deliverables` table and the two nullable `entries` columns that reference it.
         *
         * Purely additive — no existing table is rewritten and no existing row changes meaning. Every
         * entry migrates with a NULL deliverable, which is exactly right and needs no back-fill: "not
         * part of a deliverable" is the normal state, not a gap (nothing guessed one before, and
         * inventing one retroactively would be a claim the user never made). Deliverables accrue only as
         * the user creates them and files into them.
         *
         * ⚠️ The CREATE statements below must match Room's generated schema **exactly** (column order,
         * types, NOT NULL, and the auto-generated index names) or the identity hash check fails at open
         * with "Migration didn't properly handle" — and `exportSchema` is off, so there is no JSON to
         * diff against. Cross-check against [DeliverableEntity]:
         *  - `done: Boolean` (non-null, no default) → `INTEGER NOT NULL`;
         *  - `description: String?` → `TEXT` (nullable);
         *  - Kotlin defaults (`done = false`, `sortOrder = 0`) are **constructor** defaults, not SQL
         *    ones — Room never emits a DEFAULT clause for them, so neither does this.
         *  - index names follow Room's `index_<table>_<col>_<col>` convention (see [MIGRATION_2_3],
         *    where getting that wrong was already called out).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `deliverables` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`project` TEXT NOT NULL, " +
                        "`goalArea` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`description` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_deliverables_name_project_goalArea` " +
                        "ON `deliverables` (`name`, `project`, `goalArea`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_deliverables_project_goalArea` " +
                        "ON `deliverables` (`project`, `goalArea`)",
                )
                db.execSQL("ALTER TABLE entries ADD COLUMN deliverable TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN anchorDeliverable TEXT")
            }
        }
    }
}
