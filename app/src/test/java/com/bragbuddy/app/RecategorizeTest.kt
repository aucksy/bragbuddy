package com.bragbuddy.app

import com.bragbuddy.app.data.entry.Recategorize
import com.bragbuddy.app.data.entry.Recategorize.FolderRef
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the pure Recategorize helpers (Phase 2 · fix-a-wrong-category preselection logic). */
class RecategorizeTest {

    private fun pillar(name: String, kind: PillarKind) =
        Pillar(id = name.lowercase().replace(' ', '-'), name = name, kind = kind, blurb = "")

    private val framework = Framework(
        listOf(
            pillar("Performance Goals", PillarKind.GOAL_AREA),
            pillar("Learning & Growth", PillarKind.DEVELOPMENT),
            pillar("Leadership & Behaviours", PillarKind.BEHAVIOUR),
            pillar("Collaboration", PillarKind.BEHAVIOUR),
        ),
    )

    private val folders = listOf(
        FolderRef("Raven Migration", "Performance Goals"),
        FolderRef("Reading", "Learning & Growth"),
    )

    private fun entry(
        goalCategory: String? = null,
        project: String? = null,
        demonstrates: List<String> = emptyList(),
    ) = EntryEntity(
        createdAt = 0L,
        source = EntrySource.TEXT,
        status = EntryStatus.PROCESSED,
        rawTranscript = "x",
        goalCategory = goalCategory,
        project = project,
        demonstrates = demonstrates,
    )

    // ---------------- category / behaviour partitioning ----------------

    @Test
    fun `placement categories are goal-area plus development, never behaviours`() {
        assertThat(Recategorize.placementCategories(framework).map { it.name })
            .containsExactly("Performance Goals", "Learning & Growth").inOrder()
    }

    @Test
    fun `behaviour categories are only behaviours`() {
        assertThat(Recategorize.behaviourCategories(framework).map { it.name })
            .containsExactly("Leadership & Behaviours", "Collaboration").inOrder()
    }

    // ---------------- default category ----------------

    @Test
    fun `default category keeps the current placement when it still matches`() {
        assertThat(Recategorize.defaultCategory(entry(goalCategory = "Learning & Growth"), framework))
            .isEqualTo("Learning & Growth")
    }

    @Test
    fun `default category canonicalises a case-different current goal area`() {
        assertThat(Recategorize.defaultCategory(entry(goalCategory = "performance goals"), framework))
            .isEqualTo("Performance Goals")
    }

    @Test
    fun `default category falls back to the first when current is a behaviour, unknown, or null`() {
        // A behaviour is NOT a placement category — falls back to the first goal/growth pillar.
        assertThat(Recategorize.defaultCategory(entry(goalCategory = "Leadership & Behaviours"), framework))
            .isEqualTo("Performance Goals")
        assertThat(Recategorize.defaultCategory(entry(goalCategory = "Inbox"), framework))
            .isEqualTo("Performance Goals")
        assertThat(Recategorize.defaultCategory(entry(goalCategory = null), framework))
            .isEqualTo("Performance Goals")
    }

    @Test
    fun `default category is null when the framework has no placement categories`() {
        val behavioursOnly = Framework(listOf(pillar("Leadership", PillarKind.BEHAVIOUR)))
        assertThat(Recategorize.defaultCategory(entry(goalCategory = "Performance Goals"), behavioursOnly)).isNull()
    }

    // ---------------- default folder ----------------

    @Test
    fun `default folder matches the current project scoped to the chosen category`() {
        assertThat(Recategorize.defaultFolder(entry(project = "Raven Migration"), "Performance Goals", folders))
            .isEqualTo("Raven Migration")
        // Case-insensitive on the folder name.
        assertThat(Recategorize.defaultFolder(entry(project = "raven migration"), "Performance Goals", folders))
            .isEqualTo("Raven Migration")
    }

    @Test
    fun `default folder is null when the project belongs to a different category`() {
        // "Raven Migration" is under Performance Goals — not a match while the chosen category is Learning.
        assertThat(Recategorize.defaultFolder(entry(project = "Raven Migration"), "Learning & Growth", folders)).isNull()
    }

    @Test
    fun `default folder is null for outside-project, no project, or no category`() {
        assertThat(Recategorize.defaultFolder(entry(project = "Outside-project"), "Performance Goals", folders)).isNull()
        assertThat(Recategorize.defaultFolder(entry(project = null), "Performance Goals", folders)).isNull()
        assertThat(Recategorize.defaultFolder(entry(project = "Raven Migration"), null, folders)).isNull()
    }

    // ---------------- default behaviours ----------------

    @Test
    fun `default behaviours precheck current tags, canonicalised, dropping stale ones`() {
        assertThat(Recategorize.defaultBehaviours(entry(demonstrates = listOf("leadership & behaviours", "Collaboration")), framework))
            .containsExactly("Leadership & Behaviours", "Collaboration")
        // A tag that no longer matches any behaviour pillar is dropped (it was already invisible on Home).
        assertThat(Recategorize.defaultBehaviours(entry(demonstrates = listOf("Retired Skill")), framework)).isEmpty()
        assertThat(Recategorize.defaultBehaviours(entry(demonstrates = emptyList()), framework)).isEmpty()
    }
}
