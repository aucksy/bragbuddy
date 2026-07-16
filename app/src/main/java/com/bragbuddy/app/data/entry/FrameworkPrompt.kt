package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.local.ProjectEntity

/**
 * Builds the `{{APPRAISAL_FRAMEWORK}}` block for the **daily categorizer** (BragBuddy-System-Prompt
 * PART A). Pure so it can be unit-tested away from the pipeline.
 *
 * **GOAL AREAS = names only** (Phase B2b): a goal-area category's detail blurb feeds the **summary only**
 * ([Framework.toPromptBlock] → PART B), so editing it changes future summaries without disturbing daily
 * filing. The **full project details** (name + goal area + description) ride separately in `{{PROJECTS}}`.
 *
 * **BEHAVIOURS / DEVELOPMENT = name + blurb (AI-1).** These pillars render their descriptions again so
 * the model can tag work to the behaviour it *genuinely* evidences, judged against the blurb — the fix
 * for the demonstrates-tagging accuracy the AI-0 baseline flagged. (These blurbs aren't a placement
 * slot, so no B2b filing concern.) Sub-folder names still ride along under any category.
 *
 * **A DEVELOPMENT area's sub-folders are PROJECTS (v0.31.0)**, labelled as such — not "focus areas".
 * A development area is a real placement target (the model may file into it), so its folders are real
 * placement slots and [EntryProcessor.prepare] now offers them in `{{PROJECTS}}` too. Labelling them
 * "focus areas" here while the model was allowed to file into the area — but never given a folder to
 * file into — is what forced every development-area entry to "Outside-project". A BEHAVIOUR's folders
 * remain "focus areas": behaviours are tagged via `demonstrates`, never filed into.
 */
internal object FrameworkPrompt {
    fun categorizerBlock(fw: Framework, projects: List<ProjectEntity>): String {
        val byCategory = projects.groupBy { it.goalArea.trim().lowercase() }
        fun subsOf(name: String): String =
            byCategory[name.trim().lowercase()].orEmpty().joinToString(", ") { it.name }
        fun StringBuilder.line(p: Pillar, label: String, withBlurb: Boolean) {
            append("- ").append(p.name)
            if (withBlurb && p.blurb.isNotBlank()) append(": ").append(p.blurb)
            val s = subsOf(p.name)
            if (s.isNotBlank()) append(" · ").append(label).append(": ").append(s)
            appendLine()
        }
        return buildString {
            appendLine("GOAL AREAS (results / projects map here):")
            fw.goalAreas.forEach { line(it, "projects", withBlurb = false) }
            appendLine("BEHAVIOURS / COMPETENCIES (tag work that demonstrates these):")
            fw.behaviours.forEach { line(it, "focus areas", withBlurb = true) }
            if (fw.development.isNotEmpty()) {
                appendLine("DEVELOPMENT (optional):")
                fw.development.forEach { line(it, "projects", withBlurb = true) }
            }
        }.trim()
    }
}
