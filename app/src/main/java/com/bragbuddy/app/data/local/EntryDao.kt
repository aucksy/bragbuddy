package com.bragbuddy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    /** Inserting the raw transcript is the durable first step of capture; returns the new row id. */
    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: EntryStatus): Flow<List<EntryEntity>>

    /** The Inbox surface: low-confidence / unplaceable (INBOX) and AI-failure (FAILED) entries. */
    @Query("SELECT * FROM entries WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeIn(statuses: List<EntryStatus>): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries WHERE status IN (:statuses)")
    fun observeCountIn(statuses: List<EntryStatus>): Flow<Int>

    /** All entries still awaiting AI processing (drained on launch in case a run was interrupted). */
    @Query("SELECT * FROM entries WHERE status = :status ORDER BY createdAt ASC")
    suspend fun listByStatus(status: EntryStatus = EntryStatus.RAW): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    /** The user's existing routine-work labels, most-used first (AI-1 · {{ROUTINE_TYPES}}). Fed to the
     *  categorizer so the model reuses a fitting label instead of coining a near-duplicate variant. */
    @Query(
        "SELECT routineType FROM entries WHERE routineType IS NOT NULL AND routineType != '' " +
            "GROUP BY routineType ORDER BY COUNT(*) DESC LIMIT 20",
    )
    suspend fun distinctRoutineTypes(): List<String>

    /** Toggle the ★ Standout flag (Phase 4 entry detail). Targeted update — can't race a re-file. */
    @Query("UPDATE entries SET isExtra = :value WHERE id = :id")
    suspend fun setExtra(id: Long, value: Boolean)

    /** Toggle the pin-for-summary flag (Phase 4 entry detail; consumed by the Phase 5 summary). */
    @Query("UPDATE entries SET isPinned = :value WHERE id = :id")
    suspend fun setPinned(id: Long, value: Boolean)

    /** Relabel every entry filed under a renamed goal-area category (Phase B2 · category rename-remap).
     *  Case-insensitive match on the OLD name. NB: the param is `newName`, NOT `new` — Room generates
     *  Java from this DAO and `new` is a Java reserved word (it produced an invalid `EntryDao_Impl.java`).
     *  The `demonstrates` JSON list (behaviour tags) is a list column SQL can't touch — the processor
     *  rewrites those rows in Kotlin. */
    @Query("UPDATE entries SET goalCategory = :newName WHERE LOWER(goalCategory) = LOWER(:old)")
    suspend fun updateGoalCategory(old: String, newName: String)

    /** How many filed records are tagged to a project [name] **under goal area [area]** — decides
     *  whether to offer the project rename-remap prompt (Phase B2b). Scoped by goal area because a
     *  folder is unique by (name, goalArea): the same project name can live under two goal areas, and
     *  only the records of the renamed one should be counted. Case-insensitive. */
    @Query("SELECT COUNT(*) FROM entries WHERE LOWER(project) = LOWER(:name) AND LOWER(goalCategory) = LOWER(:area)")
    suspend fun countProjectReferences(name: String, area: String): Int

    /** Re-tag every record of the renamed project ([old] under goal area [oldArea]) to [newName] under
     *  [newArea] (Phase B2b · rename-remap). Scoped by the OLD goal area so a same-named folder under a
     *  different goal area is never touched; sets the goal-area label to [newArea] so records follow the
     *  folder even when its category changed (carry) or when reassigned to another goal area. NB: the
     *  params are `newName`/`newArea`, NOT `new` — Room generates Java and `new` is a reserved word. */
    @Query(
        "UPDATE entries SET project = :newName, goalCategory = :newArea " +
            "WHERE LOWER(project) = LOWER(:old) AND LOWER(goalCategory) = LOWER(:oldArea)",
    )
    suspend fun remapProjectScoped(old: String, oldArea: String, newName: String, newArea: String)

    /** Follow the rename in the deterministic anchor too, scoped to the same (old name, old goal area)
     *  so a later edit re-files back into the new folder (Phase B2b). Case-insensitive.
     *
     *  MUST run BEFORE [remapProjectScoped]: this WHERE reads `goalCategory`, which that query rewrites
     *  to the new area — so running it second means the clause never matches whenever the goal area
     *  actually changes, silently leaving the anchor pointing at the old folder (fixed v0.31.0).
     *
     *  [anchorGoalArea] follows only when it was ALREADY set: a null there means "the user never pinned
     *  a category", and a remap must not invent a pin they didn't ask for. */
    @Query(
        "UPDATE entries SET anchorProject = :newName, " +
            "anchorGoalArea = CASE WHEN anchorGoalArea IS NOT NULL THEN :newArea ELSE NULL END " +
            "WHERE LOWER(anchorProject) = LOWER(:old) AND LOWER(goalCategory) = LOWER(:oldArea)",
    )
    suspend fun remapAnchorScoped(old: String, oldArea: String, newName: String, newArea: String)

    /** Follow a CATEGORY rename in the manual category anchor (v0.31.0). Without this, a renamed
     *  category lives on in `anchorGoalArea` and the entry's next edit re-files it under the OLD name —
     *  orphaning it from the framework (it would surface only in the "Uncategorized" catch-all).
     *  Mirrors [updateGoalCategory]. Case-insensitive. */
    @Query("UPDATE entries SET anchorGoalArea = :newName WHERE LOWER(anchorGoalArea) = LOWER(:old)")
    suspend fun updateAnchorGoalArea(old: String, newName: String)

    /** A DELETED category can't stay pinned: null the manual category anchor so a later edit re-files
     *  the entry into a live category (via the AI) instead of freezing it in one that no longer exists
     *  (v0.31.0). The filed `goalCategory` label is deliberately left as-is — the "Uncategorized"
     *  catch-all still surfaces it, matching the existing folder-delete behaviour. Case-insensitive. */
    @Query("UPDATE entries SET anchorGoalArea = NULL WHERE LOWER(anchorGoalArea) = LOWER(:area)")
    suspend fun clearAnchorGoalArea(area: String)

    // ---------------- deliverables (the third axis, v0.33.0) ----------------
    //
    // All three below are scoped by (project, goalCategory) as well as the deliverable name, because a
    // deliverable is unique by (name, project, goalArea) — its name alone is NOT an identity. Scoping by
    // name only would hit a same-named deliverable ("Phase 1") under a completely different project,
    // which is the exact class of bug `remapProjectScoped`'s goal-area scoping already exists to prevent.

    /** How many filed records are tagged to deliverable [name] under ([project], [area]) — decides
     *  whether a rename needs the remap, and whether a delete needs to warn. Case-insensitive. */
    @Query(
        "SELECT COUNT(*) FROM entries WHERE LOWER(deliverable) = LOWER(:name) " +
            "AND LOWER(project) = LOWER(:project) AND LOWER(goalCategory) = LOWER(:area)",
    )
    suspend fun countDeliverableReferences(name: String, project: String, area: String): Int

    /** Re-tag every record of a renamed deliverable. Rewrites the filed label AND the anchor in ONE
     *  statement — unlike the project remap, which needs two ordered queries because the anchor's WHERE
     *  reads `goalCategory` (the column the other rewrites). Here neither `project` nor `goalCategory`
     *  changes, so there is no ordering hazard to get wrong.
     *
     *  `anchorDeliverable` follows only where it was ALREADY set: a null means the user never pinned
     *  this entry to the deliverable (v0.34.0's AI could file one without a pin), and a rename must not
     *  invent a pin they never asked for — the same rule [remapAnchorScoped] follows. */
    @Query(
        "UPDATE entries SET deliverable = :newName, " +
            "anchorDeliverable = CASE WHEN anchorDeliverable IS NOT NULL THEN :newName ELSE NULL END " +
            "WHERE LOWER(deliverable) = LOWER(:old) AND LOWER(project) = LOWER(:project) " +
            "AND LOWER(goalCategory) = LOWER(:area)",
    )
    suspend fun remapDeliverableScoped(old: String, project: String, area: String, newName: String)

    /** A DELETED deliverable can't stay tagged or pinned: clear both columns so its entries fall back to
     *  listing plainly under their project (they are NOT deleted — the entries are the record, the
     *  deliverable was only a grouping). Mirrors [clearAnchorGoalArea]'s reasoning, but also clears the
     *  filed label: unlike a category, a deliverable has no "Uncategorized" catch-all to surface it, so a
     *  dangling name would render a **ghost group header** for a deliverable that no longer exists. */
    @Query(
        "UPDATE entries SET deliverable = NULL, anchorDeliverable = NULL " +
            "WHERE LOWER(deliverable) = LOWER(:name) AND LOWER(project) = LOWER(:project) " +
            "AND LOWER(goalCategory) = LOWER(:area)",
    )
    suspend fun clearDeliverable(name: String, project: String, area: String)

    /** Clear the deliverable axis on every entry of a **deleted project**. The entries themselves are
     *  never touched — they keep their labels and fall into their category's "no specific project"
     *  bucket exactly as they already do today; only the now-dangling deliverable tag goes. */
    @Query(
        "UPDATE entries SET deliverable = NULL, anchorDeliverable = NULL " +
            "WHERE deliverable IS NOT NULL AND LOWER(project) = LOWER(:project) " +
            "AND LOWER(goalCategory) = LOWER(:area)",
    )
    suspend fun clearDeliverablesOfProject(project: String, area: String)

    /** Clear the deliverable axis on every entry under a **deleted category** (whose projects and
     *  deliverables are both cascaded away). Needed because deleting a category deliberately LEAVES the
     *  entries' `project`/`goalCategory` labels intact so the "Uncategorized" catch-all can still surface
     *  them — but a deliverable has no such catch-all, so its tag would render a ghost header. */
    @Query(
        "UPDATE entries SET deliverable = NULL, anchorDeliverable = NULL " +
            "WHERE deliverable IS NOT NULL AND LOWER(goalCategory) = LOWER(:area)",
    )
    suspend fun clearDeliverablesOfCategory(area: String)

    /**
     * The **project rename-remap** deliverable rule (v0.19.0's 3-option flow). Clears the deliverable
     * axis of the records about to move from ([old], [oldArea]) to ([target], [targetArea]) — but ONLY
     * where the tagged deliverable does not actually exist under the destination.
     *
     * This deliberately **tests reality instead of inferring intent**, because intent isn't knowable
     * here: all three options (carry / reassign to an existing project / create a new one) arrive at
     * [EntryProcessor.remapProjectEverywhere] as the same four arguments, and it is never told the
     * renamed folder's new name — so it cannot distinguish "the records are following their own folder"
     * (deliverables came along, keep the tag) from "the records are being moved to somebody else's
     * folder" (the deliverables stayed behind, drop the tag). The `NOT EXISTS` answers that directly by
     * asking whether the deliverable is present at the destination, whatever route got us here.
     *
     * ⚠️ Scoped on `project`/`goalCategory`, which [remapProjectScoped] rewrites — so it **MUST run
     * before** that query, or its WHERE stops matching. Same hazard as [remapAnchorScoped]; see the
     * ordering note there (getting it wrong cost a round in v0.31.0).
     */
    @Query(
        "UPDATE entries SET deliverable = NULL, anchorDeliverable = NULL " +
            "WHERE deliverable IS NOT NULL AND LOWER(project) = LOWER(:old) " +
            "AND LOWER(goalCategory) = LOWER(:oldArea) " +
            "AND NOT EXISTS (SELECT 1 FROM deliverables d WHERE LOWER(d.name) = LOWER(entries.deliverable) " +
            "AND LOWER(d.project) = LOWER(:target) AND LOWER(d.goalArea) = LOWER(:targetArea))",
    )
    suspend fun clearDeliverablesNotUnder(old: String, oldArea: String, target: String, targetArea: String)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM entries")
    fun observeCount(): Flow<Int>

    /** Filed wins captured in a time window (M2 weekly recap — "N wins this week"). */
    @Query("SELECT COUNT(*) FROM entries WHERE status = :status AND createdAt >= :start AND createdAt < :end")
    suspend fun countByStatusBetween(status: EntryStatus, start: Long, end: Long): Int

    /** Filed wins with a real metric, captured in a time window (M2 weekly recap — "M with numbers"). */
    @Query(
        "SELECT COUNT(*) FROM entries WHERE status = :status AND metric IS NOT NULL AND metric != '' " +
            "AND createdAt >= :start AND createdAt < :end",
    )
    suspend fun countWithMetricBetween(status: EntryStatus, start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** Count excluding one status — "is there real logged content?" checks that must not let a
     *  still-untranscribed offline voice note (PENDING_AUDIO) count as data worth backing up. */
    @Query("SELECT COUNT(*) FROM entries WHERE status != :excluded")
    suspend fun countExcluding(excluded: EntryStatus): Int

    /** Count excluding several statuses — "is there real logged content?" must ignore BOTH offline
     *  queues (PENDING_AUDIO, PENDING_IMAGE): neither is transcribed/read yet, so neither is data
     *  worth backing up or that makes the local store "non-empty" for the restore-on-connect gate. */
    @Query("SELECT COUNT(*) FROM entries WHERE status NOT IN (:excluded)")
    suspend fun countExcludingAll(excluded: List<EntryStatus>): Int

    /** Every non-null clip path referenced by ANY row. The offline-recovery orphan sweep treats these
     *  as "owned" so a clip that already drained (its row left PENDING_AUDIO but a crash skipped the
     *  file delete) is never re-adopted into a DUPLICATE entry. */
    @Query("SELECT audioPath FROM entries WHERE audioPath IS NOT NULL AND audioPath != ''")
    suspend fun allAudioPaths(): List<String>

    /** Rows that still reference a clip but have already LEFT the queue (status != PENDING_AUDIO): a
     *  drained note whose file survived a crash before its delete. Recovery deletes the file and clears
     *  the dangling reference. */
    @Query("SELECT * FROM entries WHERE audioPath IS NOT NULL AND audioPath != '' AND status != :pending")
    suspend fun settledWithAudio(pending: EntryStatus): List<EntryEntity>

    /** Null out a clip reference from the row(s) carrying it (the drained note + any split sibling that
     *  inherited the path) once its file is gone. */
    @Query("UPDATE entries SET audioPath = NULL WHERE audioPath = :path")
    suspend fun clearAudioPath(path: String)

    /** Every non-null image path referenced by ANY row — the image-queue orphan sweep's "owned" set
     *  (mirrors [allAudioPaths] for the offline image scan). */
    @Query("SELECT imagePath FROM entries WHERE imagePath IS NOT NULL AND imagePath != ''")
    suspend fun allImagePaths(): List<String>

    /** Rows that still reference an image but have already LEFT the queue (status != PENDING_IMAGE):
     *  a drained scan whose file survived a crash before its delete (mirrors [settledWithAudio]). */
    @Query("SELECT * FROM entries WHERE imagePath IS NOT NULL AND imagePath != '' AND status != :pending")
    suspend fun settledWithImage(pending: EntryStatus): List<EntryEntity>

    /** Null out an image reference from the row(s) carrying it once its file is gone (mirrors
     *  [clearAudioPath]). */
    @Query("UPDATE entries SET imagePath = NULL WHERE imagePath = :path")
    suspend fun clearImagePath(path: String)

    /** Entries the user pinned "always include" — fed live to the Phase 5 summary ({{PINNED}}). */
    @Query("SELECT * FROM entries WHERE isPinned = 1 ORDER BY createdAt DESC")
    fun observePinned(): Flow<List<EntryEntity>>

    /** The whole raw log, once (Phase 6 backup export). */
    @Query("SELECT * FROM entries ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<EntryEntity>

    /** Wipe the log (Phase 6 restore replaces it wholesale). */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    /** Insert many rows preserving their ids (Phase 6 restore). */
    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)
}
