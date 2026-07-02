package com.bragbuddy.app.data.framework

/**
 * The appraisal framework: the two-axis structure entries are organised around
 * (BragBuddy-System-Prompt PART C). GOAL AREAS are the "what" (projects + results nest here);
 * BEHAVIOURS are the "how"; DEVELOPMENT (optional) is growth.
 *
 * It ships with a sensible default so the app is usable from the first second — no blank setup and,
 * deliberately, **the company name is never asked**. The user can refine it by voice later
 * (Phase 2). One active framework is enough for the MVP.
 */
enum class PillarKind { GOAL_AREA, BEHAVIOUR, DEVELOPMENT }

data class Pillar(
    val id: String,
    val name: String,
    val kind: PillarKind,
    val blurb: String,
)

data class Framework(val pillars: List<Pillar>) {
    val goalAreas: List<Pillar> get() = pillars.filter { it.kind == PillarKind.GOAL_AREA }
    val behaviours: List<Pillar> get() = pillars.filter { it.kind == PillarKind.BEHAVIOUR }
    val development: List<Pillar> get() = pillars.filter { it.kind == PillarKind.DEVELOPMENT }

    /** Serialised block injected into both prompts as {{APPRAISAL_FRAMEWORK}}. */
    fun toPromptBlock(): String = buildString {
        appendLine("GOAL AREAS (results / projects map here):")
        goalAreas.forEach { appendLine("- ${it.name}: ${it.blurb}") }
        appendLine("BEHAVIOURS / COMPETENCIES (tag work that demonstrates these):")
        behaviours.forEach { appendLine("- ${it.name}: ${it.blurb}") }
        if (development.isNotEmpty()) {
            appendLine("DEVELOPMENT (optional):")
            development.forEach { appendLine("- ${it.name}: ${it.blurb}") }
        }
    }.trim()

    companion object {
        /** The shipped default (Build Brief § "Onboarding & the default framework"). */
        val DEFAULT = Framework(
            listOf(
                Pillar(
                    id = "performance-goals",
                    name = "Performance Goals",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "What you delivered — business and delivery objectives; projects nest here.",
                ),
                Pillar(
                    id = "leadership-behaviours",
                    name = "Leadership & Behaviours",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "How you worked — ownership, decisions, collaboration, communication.",
                ),
                Pillar(
                    id = "learning-growth",
                    name = "Learning & Growth",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "Skills and development.",
                ),
            ),
        )
    }
}
