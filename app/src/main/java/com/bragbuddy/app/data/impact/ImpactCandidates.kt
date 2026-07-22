package com.bragbuddy.app.data.impact

import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus

/**
 * Selects the filed **wins that would be stronger with a number**. Pure over an already-loaded entry
 * list (no I/O), so the selection rule is unit-tested and stable.
 *
 * A candidate is a genuine, placed achievement the user could quantify:
 *  - **PROCESSED** only — RAW/INBOX/FAILED/PENDING_AUDIO aren't filed wins yet.
 *  - has a real cleaned **bullet** (a bullet-less/failed row has nothing to strengthen).
 *  - **not routine** — bulk/BAU work is counted in aggregate, so a per-item number rarely applies.
 *  - **lacks a measurable result**: no explicit [EntryEntity.metric] AND the bullet itself contains no
 *    number ([ImpactCheck]) — if either already has one, it's strong enough.
 *
 * Newest first (occurred-at if the user dated it, else captured-at), so the freshest wins surface.
 * Gating on whether AI is available (a Groq key) is the caller's job — the add-impact merge re-runs
 * the categorizer, so the hint is only shown when that can succeed.
 *
 * Since the impact coaching moved to the **deliverable level** (the app-wide Home counter card is
 * retired), the entry points are [hintFor] / [coveredTogether]: the unit being judged is one
 * deliverable's wins as a group, not every log in the record.
 */
object ImpactCandidates {

    fun from(entries: List<EntryEntity>): List<EntryEntity> =
        entries.asSequence()
            .filter { it.status == EntryStatus.PROCESSED }
            .filter { !it.routine }
            .filter { !it.bullet.isNullOrBlank() }
            .filter { lacksMeasurable(it) }
            .sortedByDescending { it.occurredAt ?: it.createdAt }
            .toList()

    /** True when neither an explicit metric nor the bullet text carries any number. */
    fun lacksMeasurable(entry: EntryEntity): Boolean =
        entry.metric.isNullOrBlank() && !ImpactCheck.hasMeasurable(entry.bullet.orEmpty())

    /**
     * True when ONE deliverable's wins, taken **together**, already carry a number AND impact-angle
     * wording — judged collectively, because the summary condenses a deliverable to one outcome-led
     * story: once its numbers and its angle exist *somewhere* among its wins, that story can be told,
     * and nagging about every remaining line is noise (the owner's rule: the message is removed).
     *
     * The two halves may come from different wins. Routine rows count toward coverage (a number is a
     * number wherever it was logged) even though they're never *candidates*.
     */
    fun coveredTogether(entries: List<EntryEntity>): Boolean {
        val filed = entries.filter { it.status == EntryStatus.PROCESSED && !it.bullet.isNullOrBlank() }
        if (filed.isEmpty()) return false
        val hasNumber = filed.any { !lacksMeasurable(it) }
        val hasAngle = filed.any {
            ImpactCheck.hasImpactAngle(it.bullet.orEmpty()) || ImpactCheck.hasImpactAngle(it.metric.orEmpty())
        }
        return hasNumber && hasAngle
    }

    /**
     * The wins to offer for strengthening in ONE deliverable — empty when the deliverable is already
     * [coveredTogether] (nothing to say) or holds no candidates. Newest first, so a tap on the hint
     * opens the freshest number-less win.
     */
    fun hintFor(entries: List<EntryEntity>): List<EntryEntity> =
        if (coveredTogether(entries)) emptyList() else from(entries)
}
