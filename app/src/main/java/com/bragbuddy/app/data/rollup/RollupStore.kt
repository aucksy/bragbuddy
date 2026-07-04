package com.bragbuddy.app.data.rollup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rollupDataStore: DataStore<Preferences> by preferencesDataStore("rollup")

/**
 * Persists the **running rollup** ([RollupState]) — the small, bounded set of per-entry projections
 * the summary reads instead of the raw log. Stored as one JSON blob (like [com.bragbuddy.app.data
 * .framework.FrameworkStore]); Room stays untouched (no migration).
 *
 * All writes go through [com.bragbuddy.app.data.entry.EntryProcessor] under its processing mutex, so
 * a targeted [put]/[remove] can't race a concurrent re-file; DataStore's own edit serialisation is a
 * second guard. Each op is a read-modify-write of the bounded list keyed by [RollupItem.id], so an
 * edit/delete/move is exactly reversible without ever re-scanning the entry log.
 */
@Singleton
class RollupStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.rollupDataStore

    val state: Flow<RollupState> = store.data.map { prefs -> decode(prefs[KEY_ROLLUP]) }

    /** Insert or replace one entry's contribution (upsert by id). */
    suspend fun put(item: RollupItem) = store.edit { prefs ->
        val items = decode(prefs[KEY_ROLLUP]).items.filterNot { it.id == item.id } + item
        prefs[KEY_ROLLUP] = json.encodeToString(RollupState.serializer(), RollupState(items))
    }

    /** Drop an entry's contribution entirely (delete / it stopped being a filed placement). */
    suspend fun remove(id: Long) = store.edit { prefs ->
        val current = decode(prefs[KEY_ROLLUP]).items
        if (current.none { it.id == id }) return@edit
        prefs[KEY_ROLLUP] = json.encodeToString(RollupState.serializer(), RollupState(current.filterNot { it.id == id }))
    }

    /** Replace the whole set — the launch-time reconcile that seeds/repairs from processed entries. */
    suspend fun replaceAll(items: List<RollupItem>) = store.edit { prefs ->
        prefs[KEY_ROLLUP] = json.encodeToString(RollupState.serializer(), RollupState(items))
    }

    private fun decode(raw: String?): RollupState =
        if (raw.isNullOrBlank()) RollupState()
        else runCatching { json.decodeFromString(RollupState.serializer(), raw) }.getOrDefault(RollupState())

    private companion object {
        val KEY_ROLLUP = stringPreferencesKey("rollup_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}
