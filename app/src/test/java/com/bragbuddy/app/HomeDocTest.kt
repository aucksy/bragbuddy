package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.home.OUTSIDE_PROJECT_LABEL
import com.bragbuddy.app.ui.home.buildHomeDoc
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the Home living-document shaping ([buildHomeDoc] + its grouping helpers). */
class HomeDocTest {

    private val fw = Framework.DEFAULT // Performance Goals (goal) / Leadership & Behaviours (beh) / Learning & Growth (dev)

    private var nextId = 1L
    private fun entry(
        status: EntryStatus = EntryStatus.PROCESSED,
        project: String? = null,
        goal: String? = null,
        demonstrates: List<String> = emptyList(),
        createdAt: Long = nextId * 1000L,
    ) = EntryEntity(
        id = nextId++,
        createdAt = createdAt,
        source = EntrySource.TEXT,
        status = status,
        rawTranscript = "t",
        bullet = "b",
        project = project,
        goalCategory = goal,
        demonstrates = demonstrates,
    )

    private fun folder(name: String, area: String = "Performance Goals") =
        ProjectEntity(id = nextId++, name = name, goalArea = area, createdAt = 0)

    @Test
    fun `entries group under their goal pillar by project`() {
        val entries = listOf(
            entry(project = "Atlas redesign", goal = "Performance Goals"),
            entry(project = "Atlas redesign", goal = "Performance Goals"),
            entry(project = "Raven migration", goal = "Performance Goals"),
        )
        val folders = listOf(folder("Atlas redesign"), folder("Raven migration"))

        val doc = buildHomeDoc(entries, fw, folders)
        val perf = doc.goals.single { it.pillar.name == "Performance Goals" }

        assertThat(perf.namedProjectCount).isEqualTo(2)
        assertThat(perf.entryCount).isEqualTo(3)
        assertThat(perf.projects.first { it.name == "Atlas redesign" }.entryCount).isEqualTo(2)
    }

    @Test
    fun `unknown and outside projects collapse into one Outside bucket`() {
        val entries = listOf(
            entry(project = OUTSIDE_PROJECT, goal = "Performance Goals"),
            entry(project = "  ", goal = "Performance Goals"),
            entry(project = "Some ad-hoc name", goal = "Performance Goals"),
        )
        val doc = buildHomeDoc(entries, fw, folders = emptyList())
        val perf = doc.goals.single { it.pillar.name == "Performance Goals" }

        assertThat(perf.projects).hasSize(1)
        assertThat(perf.projects.single().name).isEqualTo(OUTSIDE_PROJECT_LABEL)
        assertThat(perf.projects.single().isOutside).isTrue()
        assertThat(perf.projects.single().entryCount).isEqualTo(3)
        assertThat(perf.namedProjectCount).isEqualTo(0)
    }

    @Test
    fun `empty folder still appears on its goal pillar`() {
        val doc = buildHomeDoc(emptyList(), fw, folders = listOf(folder("New Project")))
        val perf = doc.goals.single { it.pillar.name == "Performance Goals" }

        assertThat(perf.projects.map { it.name }).contains("New Project")
        assertThat(perf.projects.single { it.name == "New Project" }.entryCount).isEqualTo(0)
        assertThat(doc.isEmpty).isFalse()
    }

    @Test
    fun `one entry evidences a behaviour and stays under its project`() {
        val e = entry(project = "Atlas redesign", goal = "Performance Goals", demonstrates = listOf("Leadership & Behaviours"))
        val doc = buildHomeDoc(listOf(e), fw, folders = listOf(folder("Atlas redesign")))

        // Under its project…
        assertThat(doc.goals.single { it.pillar.name == "Performance Goals" }.entryCount).isEqualTo(1)
        // …and under the behaviour it demonstrates — the same single row, two views.
        val beh = doc.behaviours.single { it.pillar.name == "Leadership & Behaviours" }
        assertThat(beh.evidenceCount).isEqualTo(1)
        assertThat(beh.evidence.single().id).isEqualTo(e.id)
    }

    @Test
    fun `raw entries are processing and inbox entries peek`() {
        val entries = listOf(
            entry(status = EntryStatus.RAW),
            entry(status = EntryStatus.INBOX),
            entry(status = EntryStatus.FAILED),
        )
        val doc = buildHomeDoc(entries, fw, folders = emptyList())

        assertThat(doc.processing).hasSize(1)
        assertThat(doc.inbox?.count).isEqualTo(2)
        assertThat(doc.goals).isEmpty()
    }

    @Test
    fun `processed entry no pillar claims is surfaced in the catch-all (never lost)`() {
        // goalCategory matches no framework pillar (e.g. framework was renamed after filing) and it
        // evidences no behaviour → it must still appear somewhere on Home.
        val orphan = entry(project = "Atlas redesign", goal = "A Renamed Area That No Longer Exists")
        val doc = buildHomeDoc(listOf(orphan), fw, folders = emptyList())

        // Not under any real goal pillar…
        assertThat(doc.goals.none { it.pillar.name == "Performance Goals" && it.entryCount > 0 }).isTrue()
        // …but present in the synthetic "Uncategorized" catch-all section.
        val catchAll = doc.goals.single { it.pillar.name == "Uncategorized" }
        assertThat(catchAll.entryCount).isEqualTo(1)
        assertThat(catchAll.projects.single().entries.single().id).isEqualTo(orphan.id)
    }

    @Test
    fun `behaviour-only entry is not treated as uncategorized`() {
        // Unmatched goal area but it evidences a real behaviour → visible under that behaviour, so
        // it is NOT dumped into the catch-all.
        val e = entry(goal = "Nope", demonstrates = listOf("Leadership & Behaviours"))
        val doc = buildHomeDoc(listOf(e), fw, folders = emptyList())

        assertThat(doc.goals.any { it.pillar.name == "Uncategorized" }).isFalse()
        assertThat(doc.behaviours.single().evidenceCount).isEqualTo(1)
    }

    @Test
    fun `truly empty when no entries and no folders`() {
        val doc = buildHomeDoc(emptyList(), fw, folders = emptyList())
        assertThat(doc.isEmpty).isTrue()
        assertThat(doc.goals).isEmpty()
        assertThat(doc.behaviours).isEmpty()
        assertThat(doc.inbox).isNull()
    }
}
