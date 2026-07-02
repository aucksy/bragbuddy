package com.bragbuddy.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined project. Projects nest under a goal-area pillar (the "what" axis) and collect
 * dated bullets. The set of projects is fed to the categorizer so it can place entries
 * (BragBuddy-System-Prompt §A2 · {{PROJECTS}}).
 *
 * The appraisal *framework* (pillars) ships as static default data from day one and is not a Room
 * table yet — it becomes user-editable in Phase 2. Projects, being user-created, are stored here.
 */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["name"], unique = true)],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Display name, exactly as fed to the categorizer. Unique. */
    val name: String,
    /** The goal-area pillar this project rolls up to. */
    val goalArea: String,
    /** One-line description that rides along in the prompt to aid placement. */
    val description: String? = null,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)
