package com.bragbuddy.app.data.framework

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.frameworkDataStore: DataStore<Preferences> by preferencesDataStore("framework")

/** Persisted form of a [Pillar] — the enum rides as its name so the store is human-readable JSON. */
@Serializable
private data class PillarDto(val id: String, val name: String, val kind: String, val blurb: String)

/**
 * Persists the user's **active appraisal framework**. The framework ships as static data
 * ([Framework.DEFAULT]) and stays that way until the user refines it (by voice or by editing the
 * pillar cards) — at which point the edited set is stored here and used by the categorizer on every
 * call. Device-local; the company name is never part of it.
 */
@Singleton
class FrameworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.frameworkDataStore

    /** The active framework — the saved one, or the shipped default when nothing is saved yet. */
    val framework: Flow<Framework> = store.data.map { prefs ->
        val raw = prefs[KEY_PILLARS] ?: return@map Framework.DEFAULT
        runCatching {
            val dtos = json.decodeFromString<List<PillarDto>>(raw)
            if (dtos.isEmpty()) Framework.DEFAULT
            else Framework(dtos.map { it.toPillar() })
        }.getOrDefault(Framework.DEFAULT)
    }

    /** Replace the active framework. An empty list resets to the shipped default. */
    suspend fun save(pillars: List<Pillar>) {
        store.edit { prefs ->
            if (pillars.isEmpty()) {
                prefs.remove(KEY_PILLARS)
            } else {
                prefs[KEY_PILLARS] = json.encodeToString(pillars.map { it.toDto() })
            }
        }
    }

    /** Forget any customisation and fall back to [Framework.DEFAULT]. */
    suspend fun reset() = store.edit { it.remove(KEY_PILLARS) }

    private fun Pillar.toDto() = PillarDto(id, name, kind.name, blurb)

    private fun PillarDto.toPillar() = Pillar(
        id = id.ifBlank { name.slug() },
        name = name,
        kind = runCatching { PillarKind.valueOf(kind) }.getOrDefault(PillarKind.GOAL_AREA),
        blurb = blurb,
    )

    private companion object {
        val KEY_PILLARS = stringPreferencesKey("pillars_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** kebab-case an id from a display name (stable enough for a single-device framework). */
internal fun String.slug(): String =
    lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "pillar" }
