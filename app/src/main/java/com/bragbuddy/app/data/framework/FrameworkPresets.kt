package com.bragbuddy.app.data.framework

/**
 * Framework **templates** (VISION-FIT-ASSESSMENT §4, option B2 — approved 2026-07-21): static,
 * hand-authored starting shapes that mirror the review-form patterns real companies use, which the
 * user picks and then edits by hand. **Pure data, no AI call** — fully inside the standing rule that
 * no AI ever reshapes the framework (2026-07-07). **No company names anywhere** (owner constraint,
 * 2026-07-21): every template uses generic wording that reflects the same shape; example pillar
 * names are deliberately invented, with the detail text telling the user to replace them with the
 * exact names from their own form.
 *
 * Shapes covered (from the assessment's format research): the balanced hybrid (default) · named
 * leadership pillars · a weighted goal/KRA sheet · OKR · evidence-per-competency · a simple
 * narrative form.
 */
data class FrameworkPreset(
    val id: String,
    val title: String,
    /** One line under the title: which review-form shape this mirrors. */
    val tagline: String,
    val pillars: List<Pillar>,
)

object FrameworkPresets {

    val ALL: List<FrameworkPreset> = listOf(
        FrameworkPreset(
            id = "balanced",
            title = "Balanced",
            tagline = "Goals + behaviours + growth — fits most review forms. The default.",
            pillars = Framework.DEFAULT.pillars,
        ),

        FrameworkPreset(
            id = "named-pillars",
            title = "Named leadership pillars",
            tagline = "Your form lists named leadership behaviours and expects an example for each.",
            pillars = listOf(
                Pillar(
                    id = "performance-goals",
                    name = "Performance Goals",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "What you delivered — your objectives and project work for the year. " +
                        "Add each goal from your form here or as a project.",
                ),
                Pillar(
                    id = "leadership-behaviours",
                    name = "Leadership Behaviours",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "Replace this detail with the EXACT pillar names from your review form " +
                        "(e.g. 'Lead with vision; Grow others; Act with integrity; Deliver results'). " +
                        "Daily filing tags your work against what you write here.",
                ),
                Pillar(
                    id = "learning-growth",
                    name = "Learning & Growth",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "Skills, certifications, courses and development goals.",
                ),
            ),
        ),

        FrameworkPreset(
            id = "goal-sheet",
            title = "Goal sheet with targets (KRA)",
            tagline = "Weighted goals or KRAs, each with a target — self-rated at review time.",
            pillars = listOf(
                Pillar(
                    id = "kra-delivery",
                    name = "Delivery & Execution",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Rename to your first goal/KRA. Include its target and weight so the " +
                        "summary can speak to them — e.g. 'Target: cut turnaround 20% · Weight: 30%'.",
                ),
                Pillar(
                    id = "kra-quality",
                    name = "Quality & Process",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Rename to your second goal/KRA — include its target and weight.",
                ),
                Pillar(
                    id = "kra-stakeholders",
                    name = "Stakeholders & Team",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Rename to your third goal/KRA — include its target and weight. " +
                        "Add or remove categories to match your goal sheet exactly.",
                ),
                Pillar(
                    id = "values-behaviours",
                    name = "Values & Behaviours",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "The values or behaviours section of your form — list the named values " +
                        "exactly as your form words them.",
                ),
                Pillar(
                    id = "learning-growth",
                    name = "Learning & Growth",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "Skills and development goals.",
                ),
            ),
        ),

        FrameworkPreset(
            id = "okr",
            title = "Objectives & key results (OKR)",
            tagline = "Cycle objectives, each with measurable key results.",
            pillars = listOf(
                Pillar(
                    id = "objectives",
                    name = "Objectives",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Create one project per objective, and add each key result under it " +
                        "with its measurable target in the detail — the summary then tells each " +
                        "objective's story against its key results.",
                ),
                Pillar(
                    id = "collaboration-culture",
                    name = "Collaboration & Culture",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "How you worked — teamwork, initiative, communication, helping others succeed.",
                ),
                Pillar(
                    id = "growth",
                    name = "Growth",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "Learning and development.",
                ),
            ),
        ),

        FrameworkPreset(
            id = "per-competency",
            title = "Evidence per competency",
            tagline = "Your form asks for a written example against each named competency.",
            pillars = listOf(
                Pillar(
                    id = "key-achievements",
                    name = "Key Achievements",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Your strongest delivered work — projects nest here.",
                ),
                Pillar(
                    id = "communication-influence",
                    name = "Communication & Influence",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "Rename to a competency from your form; describe here what it means " +
                        "there, so daily filing can tag the right work against it.",
                ),
                Pillar(
                    id = "working-together",
                    name = "Working Together",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "Rename to a competency from your form — add or remove behaviour " +
                        "categories until they match your form's list one-to-one.",
                ),
                Pillar(
                    id = "decisions-judgement",
                    name = "Decisions & Judgement",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "Rename to a competency from your form.",
                ),
                Pillar(
                    id = "development-areas",
                    name = "Development Areas",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "The growth areas your form asks about.",
                ),
            ),
        ),

        FrameworkPreset(
            id = "narrative",
            title = "Simple narrative form",
            tagline = "Open questions: key achievements, challenges handled, growth.",
            pillars = listOf(
                Pillar(
                    id = "key-achievements",
                    name = "Key Achievements",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Your delivered work — feeds 'describe your key achievements'.",
                ),
                Pillar(
                    id = "challenges-fixes",
                    name = "Challenges & Fixes",
                    kind = PillarKind.GOAL_AREA,
                    blurb = "Hard problems you handled and what you did about them — feeds " +
                        "'challenges faced' and 'what could have gone better'.",
                ),
                Pillar(
                    id = "how-i-work",
                    name = "How I Work",
                    kind = PillarKind.BEHAVIOUR,
                    blurb = "Behaviours worth evidencing — collaboration, ownership, initiative.",
                ),
                Pillar(
                    id = "growth-goals",
                    name = "Growth & Goals",
                    kind = PillarKind.DEVELOPMENT,
                    blurb = "Learning, plus what you want next — feeds the development question.",
                ),
            ),
        ),
    )
}
