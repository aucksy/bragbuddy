package com.bragbuddy.app.data.impact

import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus

/**
 * Selects the filed **wins that would be stronger with a number** — the backing list for the Home
 * "Add impact" card (Phase 4). Pure over an already-loaded entry list (no I/O), so the selection rule
 * is unit-tested and stable.
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
 * the categorizer, so the card is only shown when that can succeed.
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
}
