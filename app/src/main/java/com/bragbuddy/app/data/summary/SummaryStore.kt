package com.bragbuddy.app.data.summary

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bragbuddy.app.data.ai.SummaryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.summaryDataStore: DataStore<Preferences> by preferencesDataStore("summary")

/**
 * A generated summary is a **cached artefact** (Build Brief § "Guardrails on generation"): viewing
 * the last saved one is free; only an explicit Regenerate calls the model, and only when the input
 * changed. This store caches one summary per option-set (period + length), tagged with the
 * [inputSignature] it was generated from — so the screen can tell "up to date" from "stale" with a
 * cheap string compare instead of a fresh call.
 *
 * Promote/demote reorderings are local edits saved back here (the [result] JSON), keeping the same
 * [inputSignature] — they change the *output*, not the *input*, so they never mark the summary stale.
 */
@Serializable
data class CachedSummary(
    val period: String,
    val length: String,
    /** [com.bragbuddy.app.data.rollup.RollupAggregator.signature] of the input at generation time. */
    val inputSignature: String,
    val result: SummaryResult,
    val periodRangeText: String,
    val generatedAtMillis: Long,
    /**
     * User edits (delete / edit / restore) that must survive a Regenerate (Phase 1). Carried onto each
     * fresh generation and re-applied via [applyOverrides]. Defaulted so older cached blobs decode.
     */
    val overrides: SummaryOverrides = SummaryOverrides(),
)

@Singleton
class SummaryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.summaryDataStore

    /** All cached summaries, keyed "period::length". */
    val cache: Flow<Map<String, CachedSummary>> = store.data.map { prefs -> decode(prefs[KEY_CACHE]) }

    suspend fun put(key: String, summary: CachedSummary) = store.edit { prefs ->
        val next = decode(prefs[KEY_CACHE]).toMutableMap().apply { this[key] = summary }
        prefs[KEY_CACHE] = json.encodeToString<Map<String, CachedSummary>>(next)
    }

    /** The raw cache blob for backup (empty string = nothing cached). */
    suspend fun exportRaw(): String = store.data.first()[KEY_CACHE].orEmpty()

    /** Restore the cache blob from a backup (blank clears it). */
    suspend fun importRaw(raw: String) = store.edit { prefs ->
        if (raw.isBlank()) prefs.remove(KEY_CACHE) else prefs[KEY_CACHE] = raw
    }

    private fun decode(raw: String?): Map<String, CachedSummary> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<String, CachedSummary>>(raw) }.getOrDefault(emptyMap())

    private companion object {
        val KEY_CACHE = stringPreferencesKey("summaries_json")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
