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
    /**
     * The deliverable within [project] this entry was filed into (v0.34.0), or null for loose work.
     * Only ever meaningful WITH [project] — a deliverable is unique by `(name, project, goalArea)`, so
     * the projection drops it whenever the project is absent. Defaulted so a rollup persisted before
     * this field existed still decodes (the whole blob is one JSON value; a throw resets it to empty).
     *
     * **This is the phase's real payoff.** Without it the summary's arc-merge is the model guessing
     * from wording (PART B rule 1), which cannot hold over hundreds of entries; a deliverable is the
     * user's OWN grouping, so it gives the merge a deterministic anchor to trust instead.
     */
    val deliverable: String? = null,
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

/**
 * A live fact about one deliverable that a [RollupItem] deliberately cannot carry (v0.34.0).
 *
 * `done` is state on the `deliverables` TABLE, not on an entry: marking a deliverable Done touches no
 * entry, so a copy denormalized into the rollup would silently go stale the moment the user finished
 * something. The aggregate therefore reads it live, and an entry tagged with a deliverable that no
 * longer exists simply has no fact — it still reports as active, and its entries are never lost.
 */
data class DeliverableFact(
    val name: String,
    val project: String,
    val goalArea: String,
    val done: Boolean = false,
)

/**
 * One deliverable's aggregated presence in a goal area (v0.34.0) — the deterministic anchor PART B
 * rule 2 groups on. It reports the FULL windowed count and span even though only the capped highlights
 * are shown, so the model can honestly write "a 6-month thread" from the handful of bullets it sees.
 */
data class AggDeliverable(
    val name: String,
    val project: String,
    val done: Boolean,
    /** Every windowed entry filed into it — routine ones included (this is the thread's real size). */
    val entryCount: Int,
    val firstMillis: Long,
    val lastMillis: Long,
)

/** One notable (non-routine) highlight candidate within a goal area. */
data class AggHighlight(
    val bullet: String,
    val project: String?,
    val metric: String?,
    val impact: Double,
    val isExtra: Boolean,
    val demonstrates: List<String>,
    /** The deliverable this highlight belongs to (v0.34.0), or null for loose work. Serialized as a
     *  "(deliverable: X)" tag so PART B rule 2 can collapse the thread into ONE story. */
    val deliverable: String? = null,
    /** How many exact/normalized-identical entries were merged into this highlight (de-dup, Phase 1). */
    val count: Int = 1,
    /**
     * The source [RollupItem.id]s this candidate was built from — every entry merged into it (so a
     * `count > 1` highlight carries them all). Carried CLIENT-SIDE ONLY: [RollupAggregator.serialize]
     * never emits an id, so the model neither sees nor echoes one and the prompt is unchanged.
     *
     * This is what makes a generated summary line reversible back to the record: the summary schema has
     * no id (the model can't be trusted to echo one), so the UI re-derives the link by matching a card's
     * bullet against these candidates ([com.bragbuddy.app.data.summary.SummaryResolver]) — the basis of
     * Summary-tab retag and the derived Set-aside list.
     */
    val ids: List<Long> = emptyList(),
)

/** A routine-work tally within a goal area (type → count, with any cumulative metrics). */
data class AggRoutine(val routineType: String, val count: Int, val metrics: List<String>)

/** A goal area's aggregated slice: ranked highlights, routine tallies, cumulative metrics. */
data class AggGoalArea(
    val name: String,
    val highlights: List<AggHighlight>,
    val routine: List<AggRoutine>,
    val metrics: List<String>,
    /** The deliverables this area's windowed entries were filed into (v0.34.0), ranked by size.
     *  Defaulted: an area whose work is all loose has none, which is the pre-v0.34.0 shape. */
    val deliverables: List<AggDeliverable> = emptyList(),
    /** True when this area is a DEVELOPMENT-kind framework pillar (AI-2): the serializer then heads
     *  it "DEVELOPMENT AREA:" so the summary model routes its items into `development[]`, not
     *  `goalAreas[]`. Areas not in the framework (the catch-all guarantee) stay goal areas. */
    val isDevelopment: Boolean = false,
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
