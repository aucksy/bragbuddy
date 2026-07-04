package com.bragbuddy.app.data.rollup

import kotlinx.serialization.Serializable

/**
 * The **running rollup** (Build Brief § "the record vs. the summary" · PRD three-layer design). One
 * compact, timestamped projection per FILED entry — NOT the raw transcript, NOT the un-filed rows.
 * The app maintains this set incrementally (add/remove by id) on every mutation, so the summary step
 * reads this small bounded structure and NEVER re-reads the raw log.
 *
 * Why per-entry (not pre-summed counts): the creator's review cycle is date-windowed (mid-year /
 * year-end from a configurable start month), so a summary aggregates only the window's contributions.
 * Keeping each contribution with its timestamp lets a window be aggregated from this bounded set
 * (~one year ≈ 200 items) without touching the log, and makes edits/deletes exactly reversible by id.
 * The AI still only ever sees the *aggregated* rollup ([RollupAggregator.serialize]) — bounded and
 * cheap for any size of history.
 */
@Serializable
data class RollupItem(
    /** The source [com.bragbuddy.app.data.local.EntryEntity] id — the key for reversible updates. */
    val id: Long,
    /** When the work happened (occurredAt ?: createdAt) — drives period windowing. */
    val timeMillis: Long,
    /** The goal area (pillar) this counts toward. */
    val goalArea: String,
    /** The named project, or null for Outside-project / unnamed. */
    val project: String? = null,
    /** The cleaned, appraisal-ready bullet. */
    val bullet: String,
    /** A number/result the user stated, if any. */
    val metric: String? = null,
    /** Provisional appraisal-worthiness 0.0–1.0 (the summary re-ranks with the fuller picture). */
    val impact: Double = 0.0,
    /** Repetitive/BAU work — counted in bulk rather than listed. */
    val routine: Boolean = false,
    /** Short label grouping routine work (e.g. "access requests"). */
    val routineType: String? = null,
    /** Standout / beyond-scope flag. */
    val isExtra: Boolean = false,
    /** Behaviours/competencies this genuinely evidences. */
    val demonstrates: List<String> = emptyList(),
)

/** The persisted form of the running rollup — the whole bounded item set. */
@Serializable
data class RollupState(val items: List<RollupItem> = emptyList())

// ---------------- Aggregated view (windowed, bounded — the summary's input) ----------------

/** One notable (non-routine) highlight candidate within a goal area. */
data class AggHighlight(
    val bullet: String,
    val project: String?,
    val metric: String?,
    val impact: Double,
    val isExtra: Boolean,
    val demonstrates: List<String>,
)

/** A routine-work tally within a goal area (type → count, with any cumulative metrics). */
data class AggRoutine(val routineType: String, val count: Int, val metrics: List<String>)

/** A goal area's aggregated slice: ranked highlights, routine tallies, cumulative metrics. */
data class AggGoalArea(
    val name: String,
    val highlights: List<AggHighlight>,
    val routine: List<AggRoutine>,
    val metrics: List<String>,
) {
    val entryCount: Int get() = highlights.size + routine.sumOf { it.count }
}

/** A behaviour/competency and the concrete evidence bullets drawn from notable work. */
data class AggBehaviour(val name: String, val evidence: List<String>)

/** The bounded aggregate fed (serialized) to the summary generator — never the raw log. */
data class AggregatedRollup(
    val goalAreas: List<AggGoalArea>,
    val behaviours: List<AggBehaviour>,
    /** How many windowed contributions this aggregate covers (shown under the period selector). */
    val entryCount: Int,
) {
    val isEmpty: Boolean get() = entryCount == 0
}
