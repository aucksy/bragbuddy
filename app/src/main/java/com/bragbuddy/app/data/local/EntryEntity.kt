package com.bragbuddy.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How an entry was captured. */
enum class EntrySource { VOICE, TEXT }

/**
 * Where an entry is in the pipeline.
 *  - RAW: captured, not yet processed by the AI (fire-and-forget capture always lands here first).
 *  - PROCESSED: the categorizer placed it into a project / goal area.
 *  - INBOX: low confidence or unplaceable — waits for a one-tap resolve.
 *  - FAILED: AI output failed to parse; the raw transcript is kept and it routes to Inbox.
 */
enum class EntryStatus { RAW, PROCESSED, INBOX, FAILED }

/**
 * The **raw log** — the immutable record of everything the user ever logged (Build Brief §
 * "the record vs. the summary"). One row per distinct piece of work.
 *
 * FIRM RULE: [rawTranscript] is always stored, even when AI processing fails — nothing the user
 * said is ever lost. AI-derived fields are nullable and filled in later (Phase 2+).
 */
@Entity(
    tableName = "entries",
    indices = [
        Index("createdAt"),
        Index("status"),
        Index("project"),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Epoch millis when the entry was captured. */
    val createdAt: Long,

    /** Epoch millis the work actually happened, if the user said a different day. Else null. */
    val occurredAt: Long? = null,

    val source: EntrySource,
    val status: EntryStatus = EntryStatus.RAW,

    /** The verbatim transcript (voice) or typed text. Never dropped. */
    val rawTranscript: String,

    // ---- AI-derived (null until processed; see BragBuddy-System-Prompt PART A) ----
    /** One clean, appraisal-ready bullet. */
    val bullet: String? = null,
    /** Exact project name, or "Outside-project", or "Inbox". */
    val project: String? = null,
    /** Goal-area (pillar) this counts toward, or "Inbox". */
    val goalCategory: String? = null,
    /** Behaviours/competencies this genuinely evidences (JSON list of names). */
    val demonstrates: List<String> = emptyList(),
    /** Standout / beyond-scope work — the reserved Extra flag. */
    val isExtra: Boolean = false,
    /** Provisional appraisal-worthiness 0.0–1.0 (re-judged at summary time). */
    val impact: Double? = null,
    /** Repetitive/BAU work better counted in bulk. */
    val routine: Boolean = false,
    /** Short label grouping routine work (e.g. "access requests"). */
    val routineType: String? = null,
    /** A number/result the user explicitly mentioned. */
    val metric: String? = null,
    /** Placement confidence 0.0–1.0. Below ~0.6 forces Inbox. */
    val confidence: Double? = null,
    /** Top 1–2 project guesses for a low-confidence entry (JSON list). */
    val suggestedProjects: List<String> = emptyList(),

    /** User pinned this to always appear in the generated summary (Phase 5). */
    val isPinned: Boolean = false,
)
