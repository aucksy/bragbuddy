package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.ui.home.buildHomeDoc
import com.bragbuddy.app.ui.home.exportDocument
import com.bragbuddy.app.ui.home.exportFolderBlock
import com.bragbuddy.app.data.local.ProjectEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the copy-out serialiser ([exportDocument] et al.). */
class DocExportTest {

    private val fw = Framework.DEFAULT

    private var nextId = 1L
    private fun entry(
        bullet: String?,
        project: String? = null,
        goal: String? = null,
        demonstrates: List<String> = emptyList(),
        isExtra: Boolean = false,
        raw: String = "raw text",
    ) = EntryEntity(
        id = nextId++,
        createdAt = nextId * 1000L,
        source = EntrySource.TEXT,
        status = EntryStatus.PROCESSED,
        rawTranscript = raw,
        bullet = bullet,
        project = project,
        goalCategory = goal,
        demonstrates = demonstrates,
        isExtra = isExtra,
    )

    private fun folder(name: String, area: String = "Performance Goals") =
        ProjectEntity(id = nextId++, name = name, goalArea = area, createdAt = 0)

    @Test
    fun `document exports pillar headings, project sub-headings and bullets`() {
        val entries = listOf(
            entry(bullet = "Shipped onboarding v2; drop-off down 18%.", project = "Atlas", goal = "Performance Goals"),
            entry(bullet = "Migrated billing to the new API.", project = "Atlas", goal = "Performance Goals", isExtra = true),
            entry(bullet = "Unblocked the payments team.", goal = "Nowhere", demonstrates = listOf("Leadership & Behaviours")),
        )
        val doc = buildHomeDoc(entries, fw, folders = listOf(folder("Atlas")))
        val text = exportDocument(doc)

        assertThat(text).contains("PERFORMANCE GOALS")
        assertThat(text).contains("Atlas")
        assertThat(text).contains("  • Shipped onboarding v2; drop-off down 18%.")
        assertThat(text).contains("[Standout]") // the isExtra bullet is marked
        assertThat(text).contains("LEADERSHIP & BEHAVIOURS")
        assertThat(text).contains("  • Unblocked the payments team.")
    }

    @Test
    fun `blank bullet falls back to the raw transcript, never empty`() {
        val e = entry(bullet = "  ", project = "Atlas", goal = "Performance Goals", raw = "the raw words")
        val doc = buildHomeDoc(listOf(e), fw, folders = listOf(folder("Atlas")))
        assertThat(exportDocument(doc)).contains("  • the raw words")
    }

    @Test
    fun `a folder block exports just that folder's bullets under its name`() {
        val entries = listOf(
            entry(bullet = "First win.", project = "Atlas", goal = "Performance Goals"),
            entry(bullet = "Second win.", project = "Atlas", goal = "Performance Goals"),
        )
        val doc = buildHomeDoc(entries, fw, folders = listOf(folder("Atlas")))
        val atlas = doc.goals.first { it.pillar.name == "Performance Goals" }.projects.first { it.name == "Atlas" }
        val text = exportFolderBlock(atlas)

        assertThat(text).startsWith("ATLAS")
        assertThat(text).contains("  • First win.")
        assertThat(text).contains("  • Second win.")
    }

    @Test
    fun `empty document exports as empty text`() {
        val doc = buildHomeDoc(emptyList(), fw, folders = emptyList())
        assertThat(exportDocument(doc)).isEmpty()
    }
}
