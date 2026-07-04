package com.bragbuddy.app

import com.bragbuddy.app.data.backup.BackupCodec
import com.bragbuddy.app.data.backup.BackupSettings
import com.bragbuddy.app.data.backup.BackupSnapshot
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.CaptureMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Round-trip tests for the backup serialiser (Phase 6). */
class BackupCodecTest {

    private val snapshot = BackupSnapshot(
        entries = listOf(
            EntryEntity(
                id = 7, createdAt = 1000, occurredAt = 900, source = EntrySource.VOICE,
                status = EntryStatus.PROCESSED, rawTranscript = "shipped the thing",
                anchorProject = "Atlas", bullet = "Shipped Atlas v2.", project = "Atlas",
                goalCategory = "Performance Goals", demonstrates = listOf("Leadership & Behaviours"),
                isExtra = true, impact = 0.8, routine = false, routineType = null,
                metric = "drop-off down 18%", confidence = 0.95, suggestedProjects = emptyList(),
                isPinned = true,
            ),
            EntryEntity(
                id = 8, createdAt = 1100, source = EntrySource.TEXT, status = EntryStatus.INBOX,
                rawTranscript = "some ticket", demonstrates = emptyList(), suggestedProjects = listOf("Atlas", "Raven"),
            ),
        ),
        projects = listOf(ProjectEntity(id = 3, name = "Atlas", goalArea = "Performance Goals", description = "the redesign", createdAt = 5, sortOrder = 1)),
        pillars = Framework.DEFAULT.pillars,
        settings = BackupSettings(
            reminderEnabled = false, reminderHour = 9, reminderMinute = 30,
            lastCaptureMode = CaptureMode.TYPE, jobRole = "Product Owner",
            rolePromptDismissed = true, reviewYearStartMonth = 4,
        ),
        summariesRaw = """{"YEAR_END::ONE_PAGE":{"period":"YEAR_END"}}""",
    )

    @Test
    fun `encode then decode preserves entries, projects, framework, settings and summaries`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(snapshot))!!

        assertThat(decoded.entries).hasSize(2)
        val e = decoded.entries.first { it.id == 7L }
        assertThat(e.source).isEqualTo(EntrySource.VOICE)
        assertThat(e.status).isEqualTo(EntryStatus.PROCESSED)
        assertThat(e.occurredAt).isEqualTo(900)
        assertThat(e.bullet).isEqualTo("Shipped Atlas v2.")
        assertThat(e.demonstrates).containsExactly("Leadership & Behaviours")
        assertThat(e.isExtra).isTrue()
        assertThat(e.impact).isEqualTo(0.8)
        assertThat(e.metric).isEqualTo("drop-off down 18%")
        assertThat(e.isPinned).isTrue()

        val e2 = decoded.entries.first { it.id == 8L }
        assertThat(e2.occurredAt).isNull()
        assertThat(e2.impact).isNull()
        assertThat(e2.suggestedProjects).containsExactly("Atlas", "Raven")

        assertThat(decoded.projects).hasSize(1)
        assertThat(decoded.projects.first().name).isEqualTo("Atlas")
        assertThat(decoded.projects.first().description).isEqualTo("the redesign")

        assertThat(decoded.pillars.map { it.name }).isEqualTo(Framework.DEFAULT.pillars.map { it.name })

        assertThat(decoded.settings.reminderEnabled).isFalse()
        assertThat(decoded.settings.lastCaptureMode).isEqualTo(CaptureMode.TYPE)
        assertThat(decoded.settings.jobRole).isEqualTo("Product Owner")
        assertThat(decoded.settings.reviewYearStartMonth).isEqualTo(4)

        assertThat(decoded.summariesRaw).contains("YEAR_END::ONE_PAGE")
    }

    @Test
    fun `decoding non-backup text returns null`() {
        assertThat(BackupCodec.decode("not json at all")).isNull()
        assertThat(BackupCodec.decode("""{"foo":1}""")).isNull()
    }

    @Test
    fun `a bad enum name falls back instead of throwing`() {
        val json = BackupCodec.encode(snapshot).replace("\"VOICE\"", "\"GARBAGE\"")
        val decoded = BackupCodec.decode(json)!!
        assertThat(decoded.entries.first { it.id == 7L }.source).isEqualTo(EntrySource.TEXT) // fallback
    }
}
