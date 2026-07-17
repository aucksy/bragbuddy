package com.bragbuddy.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The reserved placement the categorizer returns for work that belongs to no named project. */
const val OUTSIDE_PROJECT = "Outside-project"

/** The reserved placement/goal-area the categorizer returns when it can't place an entry. */
const val INBOX_PLACEMENT = "Inbox"

/**
 * The single user-facing label for [OUTSIDE_PROJECT] — shown wherever entries with no named project are
 * grouped (Home, the pillar view, Summary, exports).
 *
 * Reads as a *property of the entry* ("this one has no specific project"), NOT as a destination. The
 * previous label, "Outside project", read like a place you could file into — but it is a synthetic
 * per-category bucket, so every category renders its own, and choosing it says nothing about the
 * category. That mismatch made a correctly-filed entry look mis-filed (owner report, v0.31.0). This
 * wording matches what the Recategorize sheet has always called the same choice.
 */
const val NO_PROJECT_LABEL = "No specific project"

/**
 * True when [this] names a real project folder — i.e. it is not blank and not one of the reserved
 * sentinels ([OUTSIDE_PROJECT] / [INBOX_PLACEMENT]) or their display labels.
 *
 * The single definition of that test. It was previously re-implemented with raw string literals at six
 * call sites, which is how the display label and the sentinel drifted apart in the first place. Also
 * tolerates a *display* label appearing in a data field — a model can echo "No specific project" back
 * as a project name, and a summary restored from an older build may carry the legacy "Outside project".
 */
fun String?.isNamedProject(): Boolean {
    val s = this?.trim().orEmpty()
    if (s.isEmpty()) return false
    return !s.equals(OUTSIDE_PROJECT, ignoreCase = true) &&
        !s.equals(INBOX_PLACEMENT, ignoreCase = true) &&
        !s.equals(NO_PROJECT_LABEL, ignoreCase = true) &&
        !s.equals(LEGACY_OUTSIDE_LABEL, ignoreCase = true)
}

/** The pre-v0.31.0 display label. Still recognised as "no project" so older data/cached summaries
 *  (and any model echo of it) never materialise a bogus folder literally named "Outside project". */
private const val LEGACY_OUTSIDE_LABEL = "Outside project"

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
 *  - PENDING_IMAGE: an offline image scan — the downscaled JPEG is saved on-device
 *    ([EntryEntity.imagePath]) but not yet read by Groq vision. [OfflineRecovery] extracts its text
 *    when the network returns, moving the row to RAW (and deleting the image). Never processed by the
 *    categorizer while in this state.
 */
enum class EntryStatus { RAW, PROCESSED, INBOX, FAILED, PENDING_AUDIO, PENDING_IMAGE }

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
     * The user's **original words**, snapshotted the FIRST time [rawTranscript] is mutated and never
     * overwritten after. Null = never edited, i.e. [rawTranscript] still IS the original. Always read
     * it as `originalTranscript ?: rawTranscript`. (Room v7 column.)
     *
     * Why this exists (v0.32.0): an Edit seeds its editor from the AI's polished *bullet*, and
     * [EntryProcessor.replace] wrote that edited text straight into [rawTranscript] — so editing a
     * bullet **permanently replaced what the user actually said** with the AI's wording, and no backup
     * held a copy either. That is precisely the "details I may need months later" loss the record
     * exists to prevent, and it quietly contradicted the "never lose an entry" invariant
     * (`CONTEXT.md` §2), which had only ever been enforced against AI *failure*, not against the
     * user's own edits.
     *
     * A **redo (re-record) resets this to null** — a redo is starting over, so the fresh recording
     * becomes the original and the scrapped attempt is let go (owner's call, 2026-07-17). An **edit**
     * and an **add-impact append** both preserve it.
     */
    val originalTranscript: String? = null,

    /**
     * Explicit project anchor set at capture time (from tapping a project folder) **or by a manual
     * correction** (Inbox resolve / Recategorize). When present the categorizer must file this entry —
     * and its split siblings — into this exact project, skipping the guess. Null = infer the project as
     * usual. Fixes the PROJECT only; behaviour tagging stays an AI decision. (Room v2 column.)
     *
     * Holds [OUTSIDE_PROJECT] when the user deliberately chose "no specific project" — that is a
     * *decision*, not an absence, and must pin exactly like a named one (v0.31.0). Before that it was
     * skipped for Outside, so an edit let the AI silently re-guess a placement the user had corrected.
     */
    val anchorProject: String? = null,

    /**
     * Explicit goal-area (category) anchor set by a **manual correction** — the user's call on WHICH
     * CATEGORY this entry belongs to. When present the categorizer's own `goalCategory` guess is
     * overridden on every subsequent re-file (an edit, a redo, an add-impact combine). (Room v6 column.)
     *
     * Why this exists (v0.31.0): [anchorProject] pins only the project, and the category was previously
     * derived from the anchor folder's goal area — so a manual category chosen alongside "no specific
     * project" pinned NOTHING and the AI silently reverted it on the next edit. A manual placement is
     * the user's call and must survive, exactly as [isExtra] already does.
     */
    val anchorGoalArea: String? = null,

    /**
     * Absolute path of a saved-but-untranscribed voice clip (status [EntryStatus.PENDING_AUDIO]
     * only — an offline capture queued for transcription). Cleared (and the file deleted) the
     * moment transcription succeeds. Never backed up; audio stays on this device. (Room v4 column.)
     */
    val audioPath: String? = null,

    /**
     * Absolute path of a saved-but-unread scanned image (status [EntryStatus.PENDING_IMAGE] only —
     * an offline image scan queued for Groq vision). Cleared (and the file deleted) the moment the
     * extraction succeeds. Never backed up; the image stays on this device only while offline, then is
     * deleted after it's read — consistent with the privacy policy. (Room v5 column.)
     */
    val imagePath: String? = null,

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
