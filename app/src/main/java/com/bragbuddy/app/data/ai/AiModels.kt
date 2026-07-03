package com.bragbuddy.app.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed contracts for the two AI jobs (BragBuddy-System-Prompt): the frequent, cheap **daily
 * categorizer** and the rare **summary generator**. The @Serializable output types map 1:1 to the
 * JSON the model is instructed to return, so parsing is direct once a real provider is wired
 * (Phase 2 / Phase 5). Inputs are plain app types the provider serialises into the prompt.
 */

// ---------------- Daily categorizer ----------------

/** Everything the categorizer needs for one transcript (kept minimal — it rides on every call). */
data class CategorizeRequest(
    val transcript: String,
    /** ISO date, e.g. "2026-07-02". */
    val today: String,
    /** Serialised appraisal framework block ({{APPRAISAL_FRAMEWORK}}). */
    val framework: String,
    /** One line per project: name + [goal area] + short description ({{PROJECTS}}). */
    val projects: List<String>,
    /** The user's job role — context to sharpen core-duty vs. beyond-scope judgement. Blank = unset. */
    val role: String = "",
    /** Explicit project anchor (folder-tap). When set, the model must file into this exact project. */
    val projectAnchor: String? = null,
)

@Serializable
data class CategorizedEntry(
    val bullet: String = "",
    // Default to Inbox so a model that omits a placement field still parses (→ routed to Inbox),
    // rather than failing the whole entry's parse. The prompt always asks for these.
    val project: String = "Inbox",
    val goalCategory: String = "Inbox",
    val demonstrates: List<String> = emptyList(),
    val isExtra: Boolean = false,
    val impact: Double = 0.0,
    val routine: Boolean = false,
    val routineType: String? = null,
    val metric: String? = null,
    val dateMentioned: String? = null,
    val confidence: Double = 0.0,
    val suggestedProjects: List<String> = emptyList(),
)

@Serializable
data class CategorizeResult(
    val entries: List<CategorizedEntry> = emptyList(),
)

// ---------------- Summary generator ----------------

data class SummaryRequest(
    /** e.g. "mid-year" or "full-year". */
    val period: String,
    /** Free-text length cap; blank = ~one page. */
    val lengthCap: String,
    val framework: String,
    /** Pinned bullets the user insists on including. */
    val pinned: List<String>,
    /** The serialised running rollup (never the raw log). */
    val rollup: String,
    /** The user's job role — context for what reads as core vs. standout for that role. Blank = unset. */
    val role: String = "",
)

@Serializable
data class SummaryAchievement(
    val bullet: String,
    val project: String? = null,
    val metric: String? = null,
)

@Serializable
data class SummaryRolledUp(
    val bullet: String,
    val routineType: String,
    val count: Int,
)

@Serializable
data class SummaryGoalArea(
    val name: String,
    val achievements: List<SummaryAchievement> = emptyList(),
    val rolledUp: List<SummaryRolledUp> = emptyList(),
)

@Serializable
data class SummaryBehaviour(
    val name: String,
    val evidence: List<String> = emptyList(),
)

@Serializable
data class SummaryBody(
    val goalAreas: List<SummaryGoalArea> = emptyList(),
    val behaviours: List<SummaryBehaviour> = emptyList(),
    val development: List<String> = emptyList(),
)

@Serializable
data class SetAsideNote(
    val what: String,
    val why: String,
)

@Serializable
data class SummaryResult(
    val summary: SummaryBody,
    @SerialName("setAside") val setAside: List<SetAsideNote> = emptyList(),
)

// ---------------- Framework refine (Part C · one-time setup call) ----------------

/** Turn a plain-language description of how the user is judged into structured pillars. */
data class FrameworkRefineRequest(
    /** What the user spoke/typed about their review. */
    val description: String,
    /** The current framework block, so the AI refines rather than blindly replaces. */
    val currentFramework: String,
    /** The user's job role — helps seed sensible categories for that role. Blank = unset. */
    val role: String = "",
)

/** One proposed pillar. [kind] is a raw string mapped to [com.bragbuddy.app.data.framework.PillarKind]. */
@Serializable
data class RefinedPillar(
    val name: String,
    val kind: String = "GOAL_AREA",
    val blurb: String = "",
)

@Serializable
data class FrameworkRefineResult(
    val pillars: List<RefinedPillar> = emptyList(),
)
