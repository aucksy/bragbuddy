package com.bragbuddy.app.ui.home

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.INBOX_PLACEMENT
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectEntity

/**
 * Shapes the flat entry log into the **living document** the Home screen renders (Design System §1
 * · "Home — your living document"): goal/growth pillars hold projects with dated bullets; behaviour
 * pillars gather the same entries as evidence; the Inbox waits at the end.
 *
 * Pure functions over already-loaded data (no I/O), so the grouping is unit-tested and the same
 * builder feeds both the Home overview (counts) and the deep pillar view (the entries themselves).
 * "One entry, two places" is intentional: an entry appears under its project AND under each
 * behaviour it demonstrates — never a duplicated row, just two computed views of one entry.
 */

/** Label shown for entries that belong to no named project (placement [OUTSIDE_PROJECT]). */
const val OUTSIDE_PROJECT_LABEL = "Outside project"

/** Reserved id/label for the catch-all section that holds already-filed entries no current pillar
 *  claims (e.g. after the framework was renamed or refined). Guarantees nothing is ever hidden. */
const val UNCATEGORIZED_ID = "__uncategorized__"
const val UNCATEGORIZED_LABEL = "Uncategorized"

/** A project (folder) within a goal pillar, plus the entries filed into it (may be empty). */
data class ProjectBullets(
    val name: String,
    val isOutside: Boolean,
    val entries: List<EntryEntity>,
) {
    val entryCount: Int get() = entries.size

    /** Most recent activity (occurred-at if given, else captured-at); 0 for an empty folder. */
    val lastUpdated: Long get() = entries.maxOfOrNull { it.effectiveTime() } ?: 0L
}

/** A goal-area or development pillar and the projects/bullets that roll up to it. */
data class GoalSection(
    val pillar: Pillar,
    val colorIndex: Int,
    val projects: List<ProjectBullets>,
) {
    val namedProjectCount: Int get() = projects.count { !it.isOutside }
    val entryCount: Int get() = projects.sumOf { it.entryCount }
}

/** A behaviour pillar and the entries that genuinely evidence it. */
data class BehaviourSection(
    val pillar: Pillar,
    val colorIndex: Int,
    val evidence: List<EntryEntity>,
) {
    val evidenceCount: Int get() = evidence.size
    val sample: EntryEntity? get() = evidence.firstOrNull()
    val moreCount: Int get() = (evidence.size - 1).coerceAtLeast(0)
}

/** The Inbox peek at the foot of Home: how many wait, and the most recent one. */
data class InboxPeek(val count: Int, val first: EntryEntity?)

/** The whole Home document. [processing] holds just-captured RAW rows so a fresh capture is visible
 *  while the AI files it (they leave this list the moment they're placed or routed to the Inbox).
 *  [waiting] holds queued offline captures — voice notes (PENDING_AUDIO) awaiting transcription and
 *  image scans (PENDING_IMAGE) awaiting a read — saved on-device and awaiting network, kept visible
 *  so an offline capture never silently disappears. */
data class HomeDoc(
    val processing: List<EntryEntity>,
    val waiting: List<EntryEntity>,
    val goals: List<GoalSection>,
    val behaviours: List<BehaviourSection>,
    val inbox: InboxPeek?,
    val isEmpty: Boolean,
)

internal fun EntryEntity.effectiveTime(): Long = occurredAt ?: createdAt

private fun String?.matches(name: String): Boolean = this?.trim()?.equals(name, ignoreCase = true) == true

/**
 * Group a goal/growth pillar's processed entries by project. Entries whose project matches a known
 * folder are canonicalised to that folder's exact name; everything else (blank / [OUTSIDE_PROJECT] /
 * [INBOX_PLACEMENT] / an unknown name) collapses into the single [OUTSIDE_PROJECT_LABEL] bucket.
 * Folders declared under this pillar are always shown — even with zero entries — so a freshly
 * created folder appears on Home immediately. Named projects sort by recent activity; Outside last.
 */
fun goalProjectGroups(
    processed: List<EntryEntity>,
    folders: List<ProjectEntity>,
    pillar: Pillar,
): List<ProjectBullets> {
    val folderByLower = folders.associateBy { it.name.lowercase() }
    val inSection = processed.filter { it.goalCategory.matches(pillar.name) }
        .sortedByDescending { it.effectiveTime() }

    val buckets = LinkedHashMap<String, MutableList<EntryEntity>>()
    for (e in inSection) {
        val proj = e.project?.trim().orEmpty()
        val canonical = folderByLower[proj.lowercase()]?.name
        val key = canonical ?: OUTSIDE_PROJECT_LABEL
        buckets.getOrPut(key) { mutableListOf() }.add(e)
    }
    // Surface empty folders belonging to this pillar (create-a-folder → see it on Home).
    for (f in folders.filter { it.goalArea.matches(pillar.name) }) {
        if (buckets.keys.none { it.equals(f.name, ignoreCase = true) }) buckets[f.name] = mutableListOf()
    }

    return buckets.entries
        .map { (name, es) -> ProjectBullets(name, isOutside = name == OUTSIDE_PROJECT_LABEL, entries = es) }
        .sortedWith(compareBy({ it.isOutside }, { -it.lastUpdated }, { it.name.lowercase() }))
}

/** Processed entries that demonstrate [pillar], newest first (the behaviour "evidence" view). */
fun behaviourEvidence(processed: List<EntryEntity>, pillar: Pillar): List<EntryEntity> =
    processed.filter { e -> e.demonstrates.any { it.matches(pillar.name) } }
        .sortedByDescending { it.effectiveTime() }

/**
 * PROCESSED entries that **no** goal/growth pillar claims (by goal area) AND **no** behaviour pillar
 * evidences — so they'd otherwise appear on no Home surface. This happens when the framework is
 * renamed/refined after an entry was filed, or the model returns an off-by-a-word goal area. Surfaced
 * in the catch-all "Uncategorized" section so the firm "never lose an entry" invariant always holds.
 */
fun uncategorizedEntries(processed: List<EntryEntity>, framework: Framework): List<EntryEntity> {
    val goalPillars = framework.pillars.filter { it.kind != PillarKind.BEHAVIOUR }
    val behaviourPillars = framework.pillars.filter { it.kind == PillarKind.BEHAVIOUR }
    return processed.filter { e ->
        goalPillars.none { e.goalCategory.matches(it.name) } &&
            behaviourPillars.none { p -> e.demonstrates.any { it.matches(p.name) } }
    }.sortedByDescending { it.effectiveTime() }
}

/** The synthetic pillar backing the catch-all section (navigable to a read/triage deep view). */
fun uncategorizedPillar(): Pillar = Pillar(
    id = UNCATEGORIZED_ID,
    name = UNCATEGORIZED_LABEL,
    kind = PillarKind.GOAL_AREA,
    blurb = "Filed before your framework changed. Open to re-home each in a tap (edit or redo).",
)

/** Build the full Home document from the raw entry list + the active framework + the folders. */
fun buildHomeDoc(
    entries: List<EntryEntity>,
    framework: Framework,
    folders: List<ProjectEntity>,
): HomeDoc {
    val processed = entries.filter { it.status == EntryStatus.PROCESSED }
    val processing = entries.filter { it.status == EntryStatus.RAW }.sortedByDescending { it.createdAt }
    val waiting = entries.filter { it.status == EntryStatus.PENDING_AUDIO || it.status == EntryStatus.PENDING_IMAGE }
        .sortedByDescending { it.createdAt }
    val inboxEntries = entries.filter { it.status == EntryStatus.INBOX || it.status == EntryStatus.FAILED }
        .sortedByDescending { it.createdAt }

    val pillars = framework.pillars
    val goals = pillars.mapIndexedNotNull { index, pillar ->
        if (pillar.kind == PillarKind.BEHAVIOUR) return@mapIndexedNotNull null
        val groups = goalProjectGroups(processed, folders, pillar)
        if (groups.isEmpty()) null else GoalSection(pillar, index, groups)
    }
    val behaviours = pillars.mapIndexedNotNull { index, pillar ->
        if (pillar.kind != PillarKind.BEHAVIOUR) return@mapIndexedNotNull null
        val evid = behaviourEvidence(processed, pillar)
        if (evid.isEmpty()) null else BehaviourSection(pillar, index, evid)
    }

    // Catch-all: any processed entry no pillar claims and no behaviour evidences must still be seen.
    val orphans = uncategorizedEntries(processed, framework)
    val goalsWithCatchAll = if (orphans.isEmpty()) {
        goals
    } else {
        goals + GoalSection(
            pillar = uncategorizedPillar(),
            colorIndex = pillars.size, // cycles the ramp to a distinct, stable hue
            projects = listOf(ProjectBullets(OUTSIDE_PROJECT_LABEL, isOutside = true, entries = orphans)),
        )
    }

    val inbox = if (inboxEntries.isEmpty()) null else InboxPeek(inboxEntries.size, inboxEntries.first())
    // Empty only when there is genuinely nothing yet — no entries at all and no folders created.
    val isEmpty = entries.isEmpty() && folders.isEmpty()
    return HomeDoc(processing, waiting, goalsWithCatchAll, behaviours, inbox, isEmpty)
}
