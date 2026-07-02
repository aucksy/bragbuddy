package com.bragbuddy.app.data.usage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageDataStore: DataStore<Preferences> by preferencesDataStore("usage")

/**
 * DataStore-backed [UsageMeter]. Keeps a per-month summary count (reset on month rollover) plus
 * lifetime totals. Deliberately minimal — the counts are the whole point; billing is out of scope.
 *
 * [nowYearMonth] is injectable purely so the month-rollover logic is testable without a real clock.
 */
@Singleton
class DataStoreUsageMeter @Inject constructor(
    @ApplicationContext private val context: Context,
) : UsageMeter {

    private val store get() = context.usageDataStore

    private val nowYearMonth: () -> String = { YearMonth.now().toString() }

    override val counts: Flow<UsageCounts> = store.data.map { prefs ->
        val current = nowYearMonth()
        val storedMonth = prefs[KEY_YEAR_MONTH] ?: current
        // If the calendar month has advanced but nothing has been recorded yet, this month is 0.
        val monthCount = if (storedMonth == current) prefs[KEY_SUMMARY_MONTH] ?: 0 else 0
        UsageCounts(
            yearMonth = current,
            summaryGenerationsThisMonth = monthCount,
            summaryGenerationsTotal = prefs[KEY_SUMMARY_TOTAL] ?: 0,
            transcriptionSecondsTotal = prefs[KEY_TRANSCRIPTION_TOTAL] ?: 0L,
        )
    }

    override suspend fun recordSummaryGeneration() {
        store.edit { prefs ->
            val current = nowYearMonth()
            val sameMonth = (prefs[KEY_YEAR_MONTH] ?: current) == current
            val monthBase = if (sameMonth) prefs[KEY_SUMMARY_MONTH] ?: 0 else 0
            prefs[KEY_YEAR_MONTH] = current
            prefs[KEY_SUMMARY_MONTH] = monthBase + 1
            prefs[KEY_SUMMARY_TOTAL] = (prefs[KEY_SUMMARY_TOTAL] ?: 0) + 1
        }
    }

    override suspend fun recordTranscriptionSeconds(seconds: Long) {
        if (seconds <= 0) return
        store.edit { prefs ->
            prefs[KEY_TRANSCRIPTION_TOTAL] = (prefs[KEY_TRANSCRIPTION_TOTAL] ?: 0L) + seconds
        }
    }

    private companion object {
        val KEY_YEAR_MONTH = stringPreferencesKey("year_month")
        val KEY_SUMMARY_MONTH = intPreferencesKey("summary_generations_month")
        val KEY_SUMMARY_TOTAL = intPreferencesKey("summary_generations_total")
        val KEY_TRANSCRIPTION_TOTAL = longPreferencesKey("transcription_seconds_total")
    }
}
