package com.bragbuddy.app

import com.bragbuddy.app.data.entry.Recategorize
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.home.buildHomeDoc
import com.bragbuddy.app.ui.home.exportGoalBlock
import com.bragbuddy.app.ui.home.inlineView
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The **deliverable** level (v0.33.0) — the pure rules behind Category → Project → Deliverable → entries.
 *
 * Covers the three things that would silently corrupt the record if they broke: an entry never being
 * dropped from a project's rendering, a tag pointing at a deliverable that isn't really there, and the
 * inline cap turning one project into a wall of text.
 */
class DeliverableGroupingTest {

    private val fw = Framework.DEFAULT // Performance Goals (goal) / Leadership & Behaviours (beh) / Learning & Growth (dev)
    private val goalArea = "Performance Goals"

    private var nextId = 1L
    private fun entry(
        project: String? = "Payments",
        deliverable: String? = null,
        goal: String? = goalArea,
        createdAt: Long = nextId * 1000L,
    ) = EntryEntity(
        id = nextId++,
        createdAt = createdAt,
        source = EntrySource.TEXT,
        status = EntryStatus.PROCESSED,
        rawTranscript = "t",
        bullet = "bullet $nextId",
        project = project,
        goalCategory = goal,
        deliverable = deliverable,
    )

    private fun folder(name: String = "Payments", area: String = goalArea) =
        ProjectEntity(id = nextId++, name = name, goalArea = area, createdAt = 0)

    private fun deliverable(
        name: String,
        project: String = "Payments",
        area: String = goalArea,
        done: Boolean = false,
    ) = DeliverableEntity(id = nextId++, name = name, project = project, goalArea = area, done = done, createdAt = 0)

    private fun projectOf(
        entries: List<EntryEntity>,
        deliverables: List<DeliverableEntity>,
        folders: List<ProjectEntity> = listOf(folder()),
    ) = buildHomeDoc(entries, fw, folders, deliverables)
        .goals.first { it.pillar.name == goalArea }
        .projects.first { it.name == "Payments" }

    // ---------------- grouping ----------------

    @Test
    fun `groups entries under their deliverable and leaves untagged ones loose`() {
        val a = entry(deliverable = "Onboarding")
        val b = entry(deliverable = "Onboarding")
        val c = entry() // no deliverable
        val p = projectOf(listOf(a, b, c), listOf(deliverable("Onboarding")))

        assertThat(p.deliverables.map { it.name }).containsExactly("Onboarding")
        assertThat(p.deliverables.first().entries.map { it.id }).containsExactly(a.id, b.id)
        assertThat(p.loose.map { it.id }).containsExactly(c.id)
        // `entries` must stay EVERY entry: it is what the count, the timestamp and the exports mean.
        assertThat(p.entryCount).isEqualTo(3)
    }

    @Test
    fun `an empty deliverable still shows so a just-created one appears immediately`() {
        val p = projectOf(listOf(entry()), listOf(deliverable("Fresh")))
        assertThat(p.deliverables.map { it.name }).containsExactly("Fresh")
        assertThat(p.deliverables.first().entries).isEmpty()
    }

    @Test
    fun `a tag naming no live deliverable falls loose rather than materialising a ghost group`() {
        // The delete cascade clears such tags eagerly — rendering must not DEPEND on that having run.
        val orphan = entry(deliverable = "Deleted thing")
        val p = projectOf(listOf(orphan), listOf(deliverable("Onboarding")))

        assertThat(p.deliverables.map { it.name }).containsExactly("Onboarding")
        assertThat(p.loose.map { it.id }).containsExactly(orphan.id)
    }

    @Test
    fun `a deliverable belongs to one project only - a same-named one elsewhere never steals entries`() {
        val mine = entry(project = "Payments", deliverable = "Phase 1")
        val theirs = entry(project = "Data", deliverable = "Phase 1")
        val doc = buildHomeDoc(
            listOf(mine, theirs),
            fw,
            listOf(folder("Payments"), folder("Data")),
            listOf(deliverable("Phase 1", project = "Payments"), deliverable("Phase 1", project = "Data")),
        )
        val projects = doc.goals.first { it.pillar.name == goalArea }.projects
        val payments = projects.first { it.name == "Payments" }
        val data = projects.first { it.name == "Data" }

        assertThat(payments.deliverables.single().entries.map { it.id }).containsExactly(mine.id)
        assertThat(data.deliverables.single().entries.map { it.id }).containsExactly(theirs.id)
    }

    @Test
    fun `done deliverables sort last but keep their entries`() {
        val old = entry(deliverable = "Shipped")
        val now = entry(deliverable = "Live")
        val p = projectOf(listOf(old, now), listOf(deliverable("Shipped", done = true), deliverable("Live")))

        assertThat(p.deliverables.map { it.name }).containsExactly("Live", "Shipped").inOrder()
        assertThat(p.doneDeliverables.single().entries.map { it.id }).containsExactly(old.id)
    }

    @Test
    fun `the no-project bucket owns no deliverables`() {
        // "No specific project" is a synthetic grouping, not a project — nothing can live inside it.
        val loose = entry(project = null)
        val doc = buildHomeDoc(listOf(loose), fw, listOf(folder()), listOf(deliverable("Onboarding")))
        val outside = doc.goals.first { it.pillar.name == goalArea }.projects.first { it.isOutside }
        assertThat(outside.deliverables).isEmpty()
    }

    // ---------------- the inline cap ----------------

    @Test
    fun `the inline cap is spent across groups and loose entries together`() {
        // Two deliverables of 4 entries each, cap 5 → 4 from the first, 1 from the second, none loose.
        val a = (1..4).map { entry(deliverable = "A") }
        val b = (1..4).map { entry(deliverable = "B") }
        val p = projectOf(a + b, listOf(deliverable("A"), deliverable("B")))
        val view = p.inlineView(cap = 5)

        assertThat(view.groups.sumOf { it.entries.size } + view.loose.size).isEqualTo(5)
        assertThat(view.truncated).isTrue()
    }

    @Test
    fun `nothing is truncated when everything fits`() {
        val p = projectOf(listOf(entry(deliverable = "A"), entry()), listOf(deliverable("A")))
        val view = p.inlineView(cap = 10)

        assertThat(view.groups.single().entries).hasSize(1)
        assertThat(view.loose).hasSize(1)
        assertThat(view.truncated).isFalse()
    }

    @Test
    fun `an empty deliverable renders without spending the cap`() {
        val entries = (1..3).map { entry() } // all loose
        val p = projectOf(entries, listOf(deliverable("Empty")))
        val view = p.inlineView(cap = 3)

        assertThat(view.groups.map { it.name }).containsExactly("Empty")
        assertThat(view.loose).hasSize(3)
        assertThat(view.truncated).isFalse()
    }

    @Test
    fun `a done group does not spend the shared budget - it renders collapsed`() {
        val done = (1..4).map { entry(deliverable = "Shipped") }
        val live = (1..5).map { entry() }
        val p = projectOf(done + live, listOf(deliverable("Shipped", done = true)))
        val view = p.inlineView(cap = 5)

        // The live work gets its full allowance despite 4 done entries existing — a done deliverable
        // costs one collapsed row, not a share of the budget.
        assertThat(view.loose.map { it.id }).containsExactlyElementsIn(live.map { it.id })
        assertThat(view.doneGroups.single().entries).hasSize(4)
        assertThat(view.truncated).isFalse()
    }

    @Test
    fun `an expanded done group is still capped so it cannot bypass the cap entirely`() {
        // Without its own cap, opening one done deliverable would pour every entry onto Home — the wall
        // of text the cap exists to prevent. The full list stays one tap away on the folder screen.
        val p = projectOf(
            (1..9).map { entry(deliverable = "Shipped") },
            listOf(deliverable("Shipped", done = true)),
        )
        val view = p.inlineView(cap = 5)

        assertThat(view.doneGroups.single().entries).hasSize(5)
        assertThat(view.truncated).isTrue() // → "See all N"
    }

    @Test
    fun `a project with no deliverables behaves exactly as before`() {
        val entries = (1..12).map { entry() }
        val p = projectOf(entries, emptyList())
        val view = p.inlineView(cap = 10)

        assertThat(view.groups).isEmpty()
        assertThat(view.loose).hasSize(10)
        assertThat(view.truncated).isTrue()
    }

    // ---------------- the picker's pure rules ----------------

    @Test
    fun `deliverablesFor is scoped by BOTH parents`() {
        val all = listOf(
            deliverable("Phase 1", project = "Payments"),
            deliverable("Phase 1", project = "Data"),
            deliverable("Other", project = "Payments", area = "Learning & Growth"),
        )
        val offered = Recategorize.deliverablesFor(goalArea, "Payments", all)
        assertThat(offered.map { it.name }).containsExactly("Phase 1")
    }

    @Test
    fun `no named project means no deliverables to offer`() {
        val all = listOf(deliverable("Phase 1"))
        assertThat(Recategorize.deliverablesFor(goalArea, null, all)).isEmpty()
        assertThat(Recategorize.deliverablesFor(goalArea, "", all)).isEmpty()
    }

    @Test
    fun `a done deliverable is still offerable - a correction must reach what shipped`() {
        val all = listOf(deliverable("Shipped", done = true))
        assertThat(Recategorize.deliverablesFor(goalArea, "Payments", all).map { it.name })
            .containsExactly("Shipped")
    }

    @Test
    fun `defaultDeliverable preselects the current one and clears it when the project changes`() {
        val all = listOf(deliverable("Onboarding", project = "Payments"))
        val e = entry(project = "Payments", deliverable = "Onboarding")

        assertThat(Recategorize.defaultDeliverable(e, goalArea, "Payments", all)).isEqualTo("Onboarding")
        // Switching the project must not carry a deliverable that belonged to the old one.
        assertThat(Recategorize.defaultDeliverable(e, goalArea, "Data", all)).isNull()
        // Nor may switching the category.
        assertThat(Recategorize.defaultDeliverable(e, "Learning & Growth", "Payments", all)).isNull()
    }

    @Test
    fun `defaultDeliverable canonicalises case rather than echoing what the entry stored`() {
        val all = listOf(deliverable("Onboarding"))
        val e = entry(deliverable = "onboarding")
        assertThat(Recategorize.defaultDeliverable(e, goalArea, "Payments", all)).isEqualTo("Onboarding")
    }

    // ---------------- the "same name, different project" trap ----------------

    @Test
    fun `deliverablesFor never offers a same-named deliverable from another project`() {
        // The one that bit the Summary retag sheet: names like "Phase 1" / "Q1" / "Discovery" repeat
        // across projects, so resolving a tag by NAME against a new project silently adopts the wrong
        // one. Every lookup must be scoped by the full (name, project, goalArea) triple.
        val all = listOf(
            deliverable("Phase 1", project = "Payments"),
            deliverable("Phase 1", project = "Data"),
        )
        val offered = Recategorize.deliverablesFor(goalArea, "Data", all)
        assertThat(offered).hasSize(1)
        assertThat(offered.single().project).isEqualTo("Data")

        // And an entry sitting in Payments/"Phase 1" must NOT preselect Data's identically-named one.
        val e = entry(project = "Payments", deliverable = "Phase 1")
        assertThat(Recategorize.defaultDeliverable(e, goalArea, "Data", all)).isNull()
    }

    @Test
    fun `a stale category still preselects the deliverable - the candidate list is the guard`() {
        // An entry whose category was renamed or deleted keeps its old goalCategory and sits in the
        // Uncategorized catch-all. Its project and deliverable are still perfectly real, so opening the
        // detail sheet must still preselect them — a guard that demanded the entry's own category match
        // dropped the preselect here, and Apply then wiped a deliverable the user never touched.
        val all = listOf(deliverable("Onboarding", project = "Payments"))
        val stale = entry(project = "Payments", deliverable = "Onboarding", goal = "A Category Since Renamed")

        assertThat(Recategorize.defaultDeliverable(stale, goalArea, "Payments", all)).isEqualTo("Onboarding")
        // The candidate list still does the real scoping: ask about the wrong category and there is
        // nothing to offer, so nothing preselects.
        assertThat(Recategorize.defaultDeliverable(stale, "Learning & Growth", "Payments", all)).isNull()
    }

    // ---------------- export ----------------

    @Test
    fun `the export renders deliverable headings, then loose bullets, then done ones`() {
        val p = projectOf(
            listOf(entry(deliverable = "Live"), entry(), entry(deliverable = "Shipped")),
            listOf(deliverable("Live"), deliverable("Shipped", done = true)),
        )
        val text = exportGoalBlock(goalArea, listOf(p))

        assertThat(text).contains("Live")
        assertThat(text).contains("Shipped (Done)")
        assertThat(text.indexOf("Live")).isLessThan(text.indexOf("Shipped (Done)"))
        // Every bullet survives the grouping — an export that drops one is a lost record.
        assertThat(text.lines().count { it.contains("•") }).isEqualTo(3)
    }

    @Test
    fun `an empty deliverable is not exported as a bare heading`() {
        val p = projectOf(listOf(entry()), listOf(deliverable("Empty")))
        assertThat(exportGoalBlock(goalArea, listOf(p))).doesNotContain("Empty")
    }
}
