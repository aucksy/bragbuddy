package com.bragbuddy.app.data.summary

import com.bragbuddy.app.data.rollup.AggHighlight

/**
 * Resolves a **generated summary line back to the entries it came from** — the missing link that makes
 * a Summary-tab correction reach the actual record.
 *
 * Why this exists: the summary is AI-authored prose with **no id at any layer**. `RollupItem` carries
 * the source entry id, but it was historically dropped when the aggregate was built, and the PART B
 * prompt neither receives nor returns an id (asking the model to echo ids is a known failure mode, and
 * it would change the prompt → an eval gate → for no user-visible gain). So the id is not *lost*, only
 * *unplumbed*: [AggHighlight.ids] now carries it client-side, and this object re-derives which candidate
 * a rendered card corresponds to by comparing text.
 *
 * The model paraphrases — it merges arcs, tightens wording, drops filler — so an exact match is not
 * enough. We score **content-word Jaccard overlap** (the same shape the eval harness uses to judge
 * whether a rollup item survived into a summary) and demand a clear, unambiguous winner. Everything
 * here is pure and unit-tested.
 *
 * **Deliberately conservative.** A wrong match would silently re-file the wrong entry — worse than no
 * match at all, because the user believes they corrected something. So:
 *  - a candidate must clear [MIN_SCORE], and
 *  - it must beat the runner-up by [MIN_MARGIN] (an ambiguous field resolves to nothing).
 * When resolution fails the caller falls back to a summary-only override, which is always safe.
 */
object SummaryResolver {

    /** Minimum content-word overlap for a candidate to be considered the source of a line at all. */
    const val MIN_SCORE = 0.5

    /** How far the best candidate must beat the runner-up. Below this the field is ambiguous → no match. */
    const val MIN_MARGIN = 0.15

    /** A resolved link from a rendered summary line to the record rows behind it. */
    data class Match(
        /** Every source entry id (a merged `count > 1` card re-files them all). Never empty. */
        val entryIds: List<Long>,
        /** The rollup candidate that matched — its bullet is the record's own wording. */
        val candidate: AggHighlight,
        /** The overlap score that won, for diagnostics/tests. */
        val score: Double,
    )

    /**
     * Resolve [bullet] (as rendered in the summary) against this goal area's [candidates].
     * Returns null when nothing clears [MIN_SCORE] or the top two are within [MIN_MARGIN].
     *
     * Candidates with no ids (a legacy cached aggregate, or a restored set-aside line the model never
     * produced) are ignored — there is nothing to write back to.
     */
    fun resolve(bullet: String, candidates: List<AggHighlight>): Match? {
        val target = contentWords(bullet)
        if (target.isEmpty()) return null
        val scored = candidates
            .filter { it.ids.isNotEmpty() }
            .map { it to jaccard(target, contentWords(it.bullet)) }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        if (best.second < MIN_SCORE) return null
        val runnerUp = scored.getOrNull(1)?.second ?: 0.0
        if (best.second - runnerUp < MIN_MARGIN) return null
        return Match(entryIds = best.first.ids, candidate = best.first, score = best.second)
    }

    /**
     * The candidates that did NOT make it into [usedBullets] — the **real** set-aside list.
     *
     * The AI's own `setAside` notes are categorical ("Routine check-ins", "condensed to keep to one
     * page") — a label, not a restorable item, so "see it completely / restore parts of it" cannot be
     * answered from them. The client already knows what was dropped: everything it offered the model
     * that the model didn't use. Order is preserved (candidates arrive impact-ranked).
     */
    fun dropped(candidates: List<AggHighlight>, usedBullets: List<String>): List<AggHighlight> {
        if (candidates.isEmpty()) return emptyList()
        val used = usedBullets.map { contentWords(it) }.filter { it.isNotEmpty() }
        return candidates.filter { c ->
            val words = contentWords(c.bullet)
            if (words.isEmpty()) return@filter false
            // A candidate counts as "used" if ANY rendered line plausibly represents it. This side is
            // deliberately LENIENT (the opposite bias to resolve): over-matching hides an item from the
            // panel, which is merely a miss; under-matching would offer to restore something already
            // shown, producing a visible duplicate.
            used.none { jaccard(words, it) >= MIN_SCORE }
        }
    }

    /**
     * True when two lines plausibly describe the SAME work — the shared "is this already here?" test,
     * used wherever an exact text key would be too brittle (the model rewords, so the record's phrasing
     * and the model's phrasing of one win never key-match).
     */
    fun similar(a: String, b: String): Boolean =
        jaccard(contentWords(a), contentWords(b)) >= MIN_SCORE

    /** Overlap of two content-word sets: |A ∩ B| / |A ∪ B|. 1.0 = identical, 0.0 = disjoint. */
    internal fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        if (intersection == 0) return 0.0
        val union = a.size + b.size - intersection
        return intersection.toDouble() / union
    }

    /**
     * Reduce a bullet to its meaning-bearing words: lowercase, punctuation stripped, stop-words and
     * 1–2 char tokens dropped. Numbers are KEPT (a metric is the most identifying token a bullet has).
     */
    internal fun contentWords(s: String): Set<String> =
        s.lowercase()
            .split(SPLIT)
            .asSequence()
            .map { it.trim { c -> !c.isLetterOrDigit() } }
            .filter { it.length > 2 || it.any { c -> c.isDigit() } }
            .filterNot { it in STOP_WORDS }
            .toSet()

    private val SPLIT = Regex("[^\\p{L}\\p{N}%₹$€£.]+")

    /** Words too common to identify a bullet — including this domain's own filler ("delivered", "led"). */
    private val STOP_WORDS = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "was", "were", "are", "has",
        "have", "had", "its", "our", "their", "them", "they", "which", "when", "while", "then",
        "than", "also", "but", "not", "all", "any", "each", "more", "most", "some", "such", "only",
        "own", "same", "too", "very", "can", "will", "just", "get", "got", "via", "per", "out",
        "off", "over", "under", "after", "before", "during", "across", "through", "about",
    )
}
