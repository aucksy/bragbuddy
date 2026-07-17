package com.bragbuddy.app.data.backup

import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.DefaultCaptureMethod
import org.json.JSONArray
import org.json.JSONObject

/**
 * A device-local snapshot of everything worth restoring (Build Brief § Backup): the raw log, the
 * folders, the appraisal framework, the user's settings, and the cached generated summaries. It does
 * NOT include the Groq API key (a secret — stays on-device) or any audio (none is retained). The
 * running rollup is derived, so it isn't stored — it's rebuilt from the restored entries.
 */
data class BackupSettings(
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val lastCaptureMode: CaptureMode,
    val jobRole: String,
    val rolePromptDismissed: Boolean,
    val reviewYearStartMonth: Int,
    /** New field is last with a default so older callers / backups still (de)serialise cleanly. */
    val defaultCaptureMethod: DefaultCaptureMethod = DefaultCaptureMethod.SPEAK,
)

data class BackupSnapshot(
    val entries: List<EntryEntity>,
    val projects: List<ProjectEntity>,
    val pillars: List<Pillar>,
    val settings: BackupSettings,
    /** The raw SummaryStore JSON blob (cached generated summaries), or "" if none. */
    val summariesRaw: String,
    /** The deliverables table (v0.33.0). **Last, with a default**, so every existing construction site
     *  and every older backup still (de)serialises — an older file simply restores none, which is the
     *  truthful reading: there were none. */
    val deliverables: List<DeliverableEntity> = emptyList(),
)

/**
 * Pure JSON (de)serialisation of a [BackupSnapshot] — no Android / Room / DataStore dependency, so it
 * unit-tests as a plain round-trip. Uses `org.json` with lenient reads (missing/renamed fields fall
 * back to defaults) so an older or newer backup never crashes the restore.
 */
object BackupCodec {

    const val VERSION = 1

    fun encode(snapshot: BackupSnapshot): String = JSONObject().apply {
        put("version", VERSION)
        put("entries", JSONArray().apply { snapshot.entries.forEach { put(it.toJson()) } })
        put("projects", JSONArray().apply { snapshot.projects.forEach { put(it.toJson()) } })
        put("deliverables", JSONArray().apply { snapshot.deliverables.forEach { put(it.toJson()) } })
        put("pillars", JSONArray().apply { snapshot.pillars.forEach { put(it.toJson()) } })
        put("settings", snapshot.settings.toJson())
        put("summaries", snapshot.summariesRaw)
    }.toString()

    /** Parse a backup. Returns null if it's not a recognisable BragBuddy backup. Strict on purpose:
     *  restore is destructive (wholesale replace), so a foreign/partial file must be rejected here —
     *  it requires OUR version marker plus the structural keys a real backup always carries. */
    fun decode(text: String): BackupSnapshot? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        if (!root.has("version") || !root.has("entries") || !root.has("pillars")) return null
        val entries = root.optJSONArray("entries").objects().mapNotNull { it.toEntry() }
        val projects = root.optJSONArray("projects").objects().mapNotNull { it.toProject() }
        val pillars = root.optJSONArray("pillars").objects().mapNotNull { it.toPillar() }
        val settings = root.optJSONObject("settings")?.toSettings() ?: defaultSettings()
        val summaries = root.optString("summaries", "")
        // Deliberately NOT added to the required-keys gate above: a pre-v0.33.0 backup is still a valid
        // BragBuddy backup and must restore, just with no deliverables.
        val deliverables = root.optJSONArray("deliverables").objects().mapNotNull { it.toDeliverable() }
        return BackupSnapshot(entries, projects, pillars, settings, summaries, deliverables)
    }

    // ---------------- entries ----------------

    private fun EntryEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        putOpt("occurredAt", occurredAt)
        put("source", source.name)
        put("status", status.name)
        put("rawTranscript", rawTranscript)
        // The user's original words ride the backup too (v0.32.0). Dropping them would mean a Drive
        // restore silently re-destroys exactly what this column exists to protect — the v0.31.0
        // anchorGoalArea bug, repeated. An older backup has no key here → null → "never edited", which
        // is the right reading of pre-v0.32.0 data.
        putOpt("originalTranscript", originalTranscript)
        putOpt("anchorProject", anchorProject)
        // Both anchors ride the backup: they record the user's MANUAL placement decisions, and dropping
        // them would let the AI silently revert every correction on the entry's next edit after a
        // restore. An older backup simply has no key here → null → nothing was ever pinned, which is
        // exactly right for pre-v0.31.0 data.
        putOpt("anchorGoalArea", anchorGoalArea)
        // The third placement axis rides too (v0.33.0), for exactly the reason above: dropping the tag
        // would silently un-file every entry from its deliverable on a restore, and dropping the anchor
        // would let a later edit re-guess a placement the user had pinned by hand.
        putOpt("anchorDeliverable", anchorDeliverable)
        putOpt("bullet", bullet)
        putOpt("project", project)
        putOpt("goalCategory", goalCategory)
        putOpt("deliverable", deliverable)
        put("demonstrates", JSONArray(demonstrates))
        put("isExtra", isExtra)
        putOpt("impact", impact)
        put("routine", routine)
        putOpt("routineType", routineType)
        putOpt("metric", metric)
        putOpt("confidence", confidence)
        put("suggestedProjects", JSONArray(suggestedProjects))
        put("isPinned", isPinned)
    }

    private fun JSONObject.toEntry(): EntryEntity? {
        val raw = optStringOrNull("rawTranscript") ?: return null
        return EntryEntity(
            id = optLong("id", 0L),
            createdAt = optLong("createdAt", 0L),
            occurredAt = optLongOrNull("occurredAt"),
            source = enumOr(optString("source"), EntrySource.TEXT),
            status = enumOr(optString("status"), EntryStatus.PROCESSED),
            rawTranscript = raw,
            originalTranscript = optStringOrNull("originalTranscript"),
            anchorProject = optStringOrNull("anchorProject"),
            anchorGoalArea = optStringOrNull("anchorGoalArea"),
            anchorDeliverable = optStringOrNull("anchorDeliverable"),
            bullet = optStringOrNull("bullet"),
            project = optStringOrNull("project"),
            goalCategory = optStringOrNull("goalCategory"),
            deliverable = optStringOrNull("deliverable"),
            demonstrates = optJSONArray("demonstrates").strings(),
            isExtra = optBoolean("isExtra", false),
            impact = optDoubleOrNull("impact"),
            routine = optBoolean("routine", false),
            routineType = optStringOrNull("routineType"),
            metric = optStringOrNull("metric"),
            confidence = optDoubleOrNull("confidence"),
            suggestedProjects = optJSONArray("suggestedProjects").strings(),
            isPinned = optBoolean("isPinned", false),
        )
    }

    // ---------------- projects ----------------

    private fun ProjectEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("goalArea", goalArea)
        putOpt("description", description)
        put("createdAt", createdAt)
        put("sortOrder", sortOrder)
        put("archived", archived)
    }

    private fun JSONObject.toProject(): ProjectEntity? {
        val name = optStringOrNull("name") ?: return null
        val area = optStringOrNull("goalArea") ?: return null
        return ProjectEntity(
            id = optLong("id", 0L),
            name = name,
            goalArea = area,
            description = optStringOrNull("description"),
            createdAt = optLong("createdAt", 0L),
            sortOrder = optInt("sortOrder", 0),
            archived = optBoolean("archived", false),
        )
    }

    // ---------------- deliverables ----------------

    private fun DeliverableEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("project", project)
        put("goalArea", goalArea)
        put("done", done)
        putOpt("description", description)
        put("createdAt", createdAt)
        put("sortOrder", sortOrder)
    }

    /** A deliverable with no parents is unreachable — it could never render or be filed into — so a row
     *  missing either one is dropped rather than restored into limbo (mirrors [toProject]). */
    private fun JSONObject.toDeliverable(): DeliverableEntity? {
        val name = optStringOrNull("name") ?: return null
        val project = optStringOrNull("project") ?: return null
        val area = optStringOrNull("goalArea") ?: return null
        return DeliverableEntity(
            id = optLong("id", 0L),
            name = name,
            project = project,
            goalArea = area,
            done = optBoolean("done", false),
            description = optStringOrNull("description"),
            createdAt = optLong("createdAt", 0L),
            sortOrder = optInt("sortOrder", 0),
        )
    }

    // ---------------- framework pillars ----------------

    private fun Pillar.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("kind", kind.name)
        put("blurb", blurb)
    }

    private fun JSONObject.toPillar(): Pillar? {
        val name = optStringOrNull("name") ?: return null
        return Pillar(
            id = optStringOrNull("id") ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "pillar" },
            name = name,
            kind = enumOr(optString("kind"), PillarKind.GOAL_AREA),
            blurb = optString("blurb", ""),
        )
    }

    // ---------------- settings ----------------

    private fun BackupSettings.toJson() = JSONObject().apply {
        put("reminderEnabled", reminderEnabled)
        put("reminderHour", reminderHour)
        put("reminderMinute", reminderMinute)
        put("lastCaptureMode", lastCaptureMode.name)
        put("jobRole", jobRole)
        put("rolePromptDismissed", rolePromptDismissed)
        put("reviewYearStartMonth", reviewYearStartMonth)
        put("defaultCaptureMethod", defaultCaptureMethod.name)
    }

    private fun JSONObject.toSettings() = BackupSettings(
        reminderEnabled = optBoolean("reminderEnabled", true),
        reminderHour = optInt("reminderHour", 18).coerceIn(0, 23),
        reminderMinute = optInt("reminderMinute", 0).coerceIn(0, 59),
        lastCaptureMode = enumOr(optString("lastCaptureMode"), CaptureMode.SPEAK),
        jobRole = optString("jobRole", ""),
        rolePromptDismissed = optBoolean("rolePromptDismissed", false),
        reviewYearStartMonth = optInt("reviewYearStartMonth", 1).coerceIn(1, 12),
        defaultCaptureMethod = enumOr(optString("defaultCaptureMethod"), DefaultCaptureMethod.SPEAK),
    )

    private fun defaultSettings() = BackupSettings(true, 18, 0, CaptureMode.SPEAK, "", false, 1)

    // ---------------- org.json helpers ----------------

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotEmpty() }
}
