package com.bragbuddy.app

import com.bragbuddy.app.data.impact.ImpactCandidates
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the Phase 4 "Add impact" candidate selection (pure, Android-free). */
class ImpactCandidatesTest {

    private var nextId = 1L

    private fun entry(
        bullet: String? = "Did a thing",
        status: EntryStatus = EntryStatus.PROCESSED,
        routine: Boolean = false,
        metric: String? = null,
        createdAt: Long = 1_000L,
        occurredAt: Long? = null,
    ) = EntryEntity(
        id = nextId++,
        createdAt = createdAt,
        occurredAt = occurredAt,
        source = EntrySource.TEXT,
        status = status,
        rawTranscript = "raw",
        bullet = bullet,
        routine = routine,
        metric = metric,
    )

    @Test
    fun `a plain processed win with no number is a candidate`() {
        val list = ImpactCandidates.from(listOf(entry(bullet = "Shipped the checkout redesign")))
        assertThat(list).hasSize(1)
    }

    @Test
    fun `a bullet that already has a number is not a candidate`() {
        assertThat(ImpactCandidates.from(listOf(entry(bullet = "Cut drop-off by 18%")))).isEmpty()
        assertThat(ImpactCandidates.from(listOf(entry(bullet = "Onboarded five new hires")))).isEmpty() // number word
    }

    @Test
    fun `an explicit metric excludes it even if the bullet has no digits`() {
        assertThat(ImpactCandidates.from(listOf(entry(bullet = "Improved retention", metric = "up 12%")))).isEmpty()
    }

    @Test
    fun `routine and bullet-less rows are excluded`() {
        assertThat(ImpactCandidates.from(listOf(entry(routine = true)))).isEmpty()
        assertThat(ImpactCandidates.from(listOf(entry(bullet = null)))).isEmpty()
        assertThat(ImpactCandidates.from(listOf(entry(bullet = "  ")))).isEmpty()
    }

    @Test
    fun `only PROCESSED entries qualify`() {
        val statuses = listOf(EntryStatus.RAW, EntryStatus.INBOX, EntryStatus.FAILED, EntryStatus.PENDING_AUDIO)
        statuses.forEach { s ->
            assertThat(ImpactCandidates.from(listOf(entry(status = s, bullet = "A qualitative win")))).isEmpty()
        }
    }

    @Test
    fun `candidates are newest first by effective time`() {
        val old = entry(bullet = "Old win", createdAt = 100L)
        val newest = entry(bullet = "New win", createdAt = 200L)
        // occurredAt wins over createdAt when present.
        val dated = entry(bullet = "Dated win", createdAt = 50L, occurredAt = 5_000L)
        val out = ImpactCandidates.from(listOf(old, newest, dated))
        assertThat(out.map { it.bullet }).containsExactly("Dated win", "New win", "Old win").inOrder()
    }

    @Test
    fun `lacksMeasurable matches the filter`() {
        assertThat(ImpactCandidates.lacksMeasurable(entry(bullet = "Ran a workshop"))).isTrue()
        assertThat(ImpactCandidates.lacksMeasurable(entry(bullet = "Ran 3 workshops"))).isFalse()
        assertThat(ImpactCandidates.lacksMeasurable(entry(bullet = "Ran a workshop", metric = "40 attendees"))).isFalse()
    }
}
