package com.bragbuddy.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The reserved placement the categorizer returns for work that belongs to no named project. */
const val OUTSIDE_PROJECT = "Outside-project"

/** The reserved placement/goal-area the categorizer returns when it can't place an entry. */
const val INBOX_PLACEMENT = "Inbox"

/** How an entry was captured. Stored by name ([Converters]) so adding a value needs no migration. */
enum class EntrySource { VOICE, TEXT, IMAGE }

/**
 * Where an entry is in the pipeline.
 *  - RAW: captured, not yet processed by the AI (fire-and-forget capture always lands here first).
 *  - PROCESSED: the categorizer placed it into a project / goal area.
 *  - INBOX: low confidence or unplaceable — waits for a one-tap resolve.
 *  - FAILED: AI output failed to parse; the raw transcript is kept and it routes to Inbox.
 *  - PENDING_AUDIO: an offline voice note — the clip is saved on-device ([EntryEntity.audioPath])
 *    but not yet transcribed. [OfflineRecovery] transcribes it when the network returns, moving the
 *    row to RAW (and deleting the audio). Never processed by the categorizer while in this state.
 */
enum class EntryStatus { RAW, PROCESSED, INBOX, FAILED, PENDING_AUDIO }

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

    /**
     * Explicit project anchor set at capture time (from tapping a project folder). When present the
     * categorizer must file this entry — and its split siblings — into this exact project, skipping
     * the guess. Null = infer the project as usual. Fixes the PROJECT only; behaviour tagging stays
     * an AI decision. (Room v2 column.)
     */
    val anchorProject: String? = null,

    /**
     * Absolute path of a saved-but-untranscribed voice clip (status [EntryStatus.PENDING_AUDIO]
     * only — an offline capture queued for transcription). Cleared (and the file deleted) the
     * moment transcription succeeds. Never backed up; audio stays on this device. (Room v4 column.)
     */
    val audioPath: String? = null,

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
