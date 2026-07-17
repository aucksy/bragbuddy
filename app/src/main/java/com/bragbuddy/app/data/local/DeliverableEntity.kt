package com.bragbuddy.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The single user-facing word for this level — **"Deliverable"** (owner's call, 2026-07-17).
 *
 * Deliberately ONE constant, exactly like [NO_PROJECT_LABEL], because the word itself is the part most
 * likely to change: it was chosen knowing it may not fit softer, non-delivery threads (e.g. "stakeholder
 * management" isn't something you *deliver*). Every user-visible string derives from this — headers,
 * menu items, sheet titles, empty states — so a later rename is a one-line display change and **never a
 * data migration**. The table, columns and code identifiers stay `deliverable*` regardless.
 */
const val DELIVERABLE_LABEL = "Deliverable"

/**
 * A **deliverable** — the third level of the record: Category → Project → **Deliverable** → entries
 * (owner's reshape, 2026-07-17). A concrete stream of work inside a project ("Merchant onboarding"
 * inside "Payments Revamp") that collects many entries over months and eventually reads in the summary
 * as one outcome-led story rather than scattered bullets.
 *
 * ## Why a separate table and not a parent column on [ProjectEntity]
 * The spec asked for this to be decided by reading how [ProjectEntity]'s `(name, goalArea)` unique index
 * and the v0.19.0 rename-remap actually work. Both point the same way:
 *  - **The index would have to be rebuilt.** A deliverable is unique by `(name, project, goalArea)`, so
 *    two projects under one category could each own a "Phase 1". Sharing the table means widening the
 *    existing composite unique index — a destructive table-recreate migration on live user data, versus
 *    a purely additive `CREATE TABLE` here.
 *  - **Blast radius.** Every existing reader of `projects` assumes each row IS a project:
 *    `EntryProcessor.prepare`'s placement universe, `FrameworkPrompt.categorizerBlock`, the framework
 *    editor, Home's project cards, the Recategorize/Retag pickers. Sharing the table would require a
 *    `parent IS NULL` filter at every one of them, and *missing one is silent* — deliverables would leak
 *    into the categorizer's project list and the AI would start filing entries into them (precisely what
 *    v0.33.0 is specified NOT to do; that's v0.34.0's job, prompt-first and eval-gated).
 *  - **[done] is meaningless for a project.** A shared table means a column that half the rows must
 *    ignore.
 * A separate table changes the behaviour of exactly nothing that already exists.
 *
 * ## Identity is the NAME, like a project's
 * [EntryEntity.deliverable] references this row by **name**, mirroring how [EntryEntity.project]
 * references [ProjectEntity] — so the same rule follows: a rename is not free, it must be remapped
 * across every referencing column ([EntryDao.remapDeliverableScoped]), and a delete must clear the
 * anchors that point here ([EntryDao.clearDeliverable]). See [Index] below for the scoping.
 */
@Entity(
    tableName = "deliverables",
    // Unique per (name, project, goalArea) — the spec's identity. Scoped by BOTH parents, not just the
    // project name, because a project is itself only unique by (name, goalArea): "Payments" can exist
    // under two categories, and each one's "Phase 1" is a genuinely different deliverable.
    indices = [
        Index(value = ["name", "project", "goalArea"], unique = true),
        // The lookup every render does: "this project's deliverables".
        Index(value = ["project", "goalArea"]),
    ],
)
data class DeliverableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Display name. Unique within its (project, goalArea). */
    val name: String,
    /** The [ProjectEntity.name] this sits under. */
    val project: String,
    /** The [ProjectEntity.goalArea] (category name) the parent project sits under. */
    val goalArea: String,
    /**
     * **Done** = the work shipped. A done deliverable drops out of the "log into" list and reads in the
     * summary as a completed story with its outcome (v0.34.0) — but it **never hides its entries**
     * (owner's call). Active/Done is the whole lifecycle; no target date (considered, not chosen).
     *
     * A Boolean, not an enum, deliberately: the state is strictly binary, and [Converters]' enum
     * converters use `valueOf`, which **throws** on an unrecognised name — so an enum that ever gained a
     * value would make a newer backup crash an older build's restore. A Boolean cannot.
     */
    val done: Boolean = false,
    /** Optional one-line detail. Not fed to the AI in v0.33.0 (no prompt change); v0.34.0 uses it. */
    val description: String? = null,
    val createdAt: Long,
    val sortOrder: Int = 0,
)
