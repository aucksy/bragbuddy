package com.bragbuddy.app.data.deliverable

import com.bragbuddy.app.data.local.DeliverableDao
import com.bragbuddy.app.data.local.DeliverableEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place **deliverables** are created and read — the third level of the record
 * (Category → Project → **Deliverable** → entries, v0.33.0). Mirrors
 * [com.bragbuddy.app.data.project.ProjectRepository] in shape and idiom.
 *
 * A deliverable is identified by `(name, project, goalArea)`, and entries reference it **by name** —
 * so this repository owns only the deliverables table itself. The two cascades that keep it consistent
 * live where the *cause* is, not here:
 *  - the **deliverables table** follows a project/category rename or delete inside `ProjectRepository`,
 *    at the single choke point every caller already goes through;
 *  - the **entries'** `deliverable`/`anchorDeliverable` columns are cleared through `EntryProcessor`,
 *    because every entry write must happen under its mutex.
 */
@Singleton
class DeliverableRepository @Inject constructor(
    private val dao: DeliverableDao,
) {
    fun observeAll(): Flow<List<DeliverableEntity>> = dao.observeAll()

    /**
     * Create a deliverable under ([project], [goalArea]). No-op if one with this name already exists
     * there (the insert IGNOREs on conflict, so a double-tap can't duplicate). Returns the new row id,
     * or a **non-positive** value when nothing was inserted — Room returns -1 on an IGNOREd conflict,
     * and 0 here for a blank name or a missing parent. Callers must treat `<= 0` as "not created"
     * (the same contract as `ProjectRepository.create`).
     *
     * A deliverable with no real parent project is meaningless — it could never be rendered or filed
     * into — so a blank [project] is refused rather than creating an unreachable row.
     */
    suspend fun create(
        name: String,
        project: String,
        goalArea: String,
        description: String? = null,
    ): Long {
        val clean = name.trim()
        val p = project.trim()
        val area = goalArea.trim()
        if (clean.isEmpty() || p.isEmpty() || area.isEmpty()) return 0
        return dao.insert(
            DeliverableEntity(
                name = clean,
                project = p,
                goalArea = area,
                description = description?.trim()?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Rename / re-describe in place. The parents never change here — a deliverable moves only by its
     * project moving (which cascades in `ProjectRepository`).
     *
     * Returns the row's identity **BEFORE** the change, so the caller can remap the entries that
     * reference the old name — and **null when the name did not actually move**, which the caller MUST
     * treat as "do not remap".
     *
     * ⚠️ That distinction is the whole contract, and it is why this re-reads the row instead of trusting
     * the requested name. [DeliverableDao.update] is `UPDATE OR IGNORE`: a rename colliding with a
     * sibling under the same (project, goalArea) is **silently skipped — no exception, no row change**.
     * Reporting success from the requested name there was a real corruption (found in review, v0.33.0):
     * renaming "Phase 1" → "Phase 2" when "Phase 2" already exists left "Phase 1" untouched on screen
     * while the remap rewrote its entries' `deliverable` AND `anchorDeliverable` to "Phase 2" — so every
     * one of them silently jumped into an unrelated deliverable, durably (the anchor survives a re-file)
     * and with no undo. The user's own placement decision, overwritten by one they never made.
     */
    suspend fun rename(id: Long, name: String, description: String?): DeliverableEntity? {
        val existing = dao.getById(id) ?: return null
        val clean = name.trim().ifBlank { return null }
        // Refuse a colliding rename outright rather than leaning on OR IGNORE, so the intent is stated
        // here rather than inferred from a silent non-effect below.
        if (!clean.equals(existing.name, ignoreCase = true) &&
            dao.getByIdentity(clean, existing.project, existing.goalArea) != null
        ) {
            return null
        }
        dao.update(
            existing.copy(
                name = clean,
                description = description?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        // Report the EFFECT, not the intent: only a row whose name genuinely moved may be remapped.
        val after = dao.getById(id) ?: return null
        return existing.takeIf { !after.name.equals(it.name, ignoreCase = true) }
    }

    /** Mark done / re-open. Done drops it out of the log-into list; its entries stay visible. */
    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    suspend fun getById(id: Long): DeliverableEntity? = dao.getById(id)

    /** Look one up by its full identity — the only safe way, since the name alone isn't unique. */
    suspend fun byIdentity(name: String, project: String, goalArea: String): DeliverableEntity? =
        dao.getByIdentity(name.trim(), project.trim(), goalArea.trim())

    suspend fun delete(id: Long) = dao.deleteById(id)
}
