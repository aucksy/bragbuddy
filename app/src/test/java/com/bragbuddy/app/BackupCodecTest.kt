package com.bragbuddy.app

import com.bragbuddy.app.data.backup.BackupCodec
import com.bragbuddy.app.data.backup.BackupSettings
import com.bragbuddy.app.data.backup.BackupSnapshot
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.DefaultCaptureMethod
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Round-trip tests for the backup serialiser (Phase 6). */
class BackupCodecTest {

    private val snapshot = BackupSnapshot(
        entries = listOf(
            EntryEntity(
                id = 7, createdAt = 1000, occurredAt = 900, source = EntrySource.VOICE,
                status = EntryStatus.PROCESSED, rawTranscript = "shipped the thing",
                originalTranscript = "shipped the thing on tuesday with raj",
                anchorProject = "Atlas", anchorDeliverable = "Checkout rebuild",
                bullet = "Shipped Atlas v2.", project = "Atlas", deliverable = "Checkout rebuild",
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
            defaultCaptureMethod = DefaultCaptureMethod.IMAGE,
        ),
        summariesRaw = """{"YEAR_END::ONE_PAGE":{"period":"YEAR_END"}}""",
        deliverables = listOf(
            DeliverableEntity(
                id = 11, name = "Checkout rebuild", project = "Atlas", goalArea = "Performance Goals",
                done = true, description = "the v2 flow", createdAt = 6, sortOrder = 2,
            ),
        ),
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
        // The user's original words must survive a Drive restore. An un-serialised column is silently
        // dropped on restore (the v0.31.0 anchorGoalArea bug) — here that would mean the restore itself
        // destroys what the user said, which is the exact loss this column exists to prevent.
        assertThat(e.originalTranscript).isEqualTo("shipped the thing on tuesday with raj")
        // Same rule for the third placement axis (v0.33.0): dropping the tag would un-file every entry
        // from its deliverable on restore, and dropping the anchor would let the AI silently re-guess a
        // placement the user pinned by hand.
        assertThat(e.deliverable).isEqualTo("Checkout rebuild")
        assertThat(e.anchorDeliverable).isEqualTo("Checkout rebuild")

        val e2 = decoded.entries.first { it.id == 8L }
        assertThat(e2.occurredAt).isNull()
        assertThat(e2.originalTranscript).isNull() // never edited
        assertThat(e2.deliverable).isNull() // not part of one — the ordinary case
        assertThat(e2.impact).isNull()
        assertThat(e2.suggestedProjects).containsExactly("Atlas", "Raven")

        assertThat(decoded.projects).hasSize(1)
        assertThat(decoded.projects.first().name).isEqualTo("Atlas")
        assertThat(decoded.projects.first().description).isEqualTo("the redesign")

        assertThat(decoded.pillars.map { it.name }).isEqualTo(Framework.DEFAULT.pillars.map { it.name })

        assertThat(decoded.settings.reminderEnabled).isFalse()
        assertThat(decoded.settings.lastCaptureMode).isEqualTo(CaptureMode.TYPE)
        assertThat(decoded.settings.defaultCaptureMethod).isEqualTo(DefaultCaptureMethod.IMAGE)
        assertThat(decoded.settings.jobRole).isEqualTo("Product Owner")
        assertThat(decoded.settings.reviewYearStartMonth).isEqualTo(4)

        assertThat(decoded.summariesRaw).contains("YEAR_END::ONE_PAGE")

        assertThat(decoded.deliverables).hasSize(1)
        val d = decoded.deliverables.first()
        assertThat(d.id).isEqualTo(11)
        assertThat(d.name).isEqualTo("Checkout rebuild")
        assertThat(d.project).isEqualTo("Atlas")
        assertThat(d.goalArea).isEqualTo("Performance Goals")
        assertThat(d.done).isTrue()
        assertThat(d.description).isEqualTo("the v2 flow")
        assertThat(d.sortOrder).isEqualTo(2)
    }

    @Test
    fun `a pre-v0_33 backup with no deliverables key still restores`() {
        // It is still a valid BragBuddy backup — it just had none. Rejecting it, or failing on the
        // missing key, would make an older backup unrestorable for no reason.
        val json = BackupCodec.encode(snapshot).replace("\"deliverables\"", "\"ignoredLegacyKey\"")
        val decoded = BackupCodec.decode(json)!!
        assertThat(decoded.deliverables).isEmpty()
        assertThat(decoded.entries).hasSize(2) // the rest of the restore is unaffected
    }

    @Test
    fun `a deliverable with no parents is dropped rather than restored unreachable`() {
        val json = BackupCodec.encode(snapshot).replace("\"project\":\"Atlas\",\"goalArea\"", "\"goalArea\"")
        val decoded = BackupCodec.decode(json)!!
        assertThat(decoded.deliverables).isEmpty()
    }

    @Test
    fun `decoding non-backup text returns null`() {
        assertThat(BackupCodec.decode("not json at all")).isNull()
        assertThat(BackupCodec.decode("""{"foo":1}""")).isNull()
    }

    @Test
    fun `an older backup with no originalTranscript key decodes as never-edited`() {
        // A pre-v0.32.0 backup simply has no key → null → "rawTranscript IS the original", which is the
        // truthful reading of that data. It must not fail the restore.
        val json = BackupCodec.encode(snapshot).replace("\"originalTranscript\"", "\"ignoredLegacyKey\"")
        val decoded = BackupCodec.decode(json)!!
        assertThat(decoded.entries.first { it.id == 7L }.originalTranscript).isNull()
    }

    @Test
    fun `a bad enum name falls back instead of throwing`() {
        val json = BackupCodec.encode(snapshot).replace("\"VOICE\"", "\"GARBAGE\"")
        val decoded = BackupCodec.decode(json)!!
        assertThat(decoded.entries.first { it.id == 7L }.source).isEqualTo(EntrySource.TEXT) // fallback
    }
}
