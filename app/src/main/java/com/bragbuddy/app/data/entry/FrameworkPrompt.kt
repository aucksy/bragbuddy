package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.local.ProjectEntity

/**
 * Builds the `{{APPRAISAL_FRAMEWORK}}` block for the **daily categorizer** (BragBuddy-System-Prompt
 * PART A). Pure so it can be unit-tested away from the pipeline.
 *
 * **Phase B2b — names, not blurbs.** The categorizer sees each category's **name** plus its **sub-folder
 * names** (context that sharpens placement + behaviour tagging), but deliberately **NOT the category
 * detail blurbs**. A category's "detail" now feeds the **summary only** ([Framework.toPromptBlock] →
 * PART B), so editing a category detail changes future summaries without disturbing daily filing.
 * Behaviours keep their names so work can still be tagged to them; the **full project details** (name +
 * goal area + description) ride separately in `{{PROJECTS}}`.
 */
internal object FrameworkPrompt {
    fun categorizerBlock(fw: Framework, projects: List<ProjectEntity>): String {
        val byCategory = projects.groupBy { it.goalArea.trim().lowercase() }
        fun subsOf(name: String): String =
            byCategory[name.trim().lowercase()].orEmpty().joinToString(", ") { it.name }
        fun StringBuilder.line(p: Pillar, label: String) {
            append("- ").append(p.name) // NAME only — the detail blurb is summary-only (Phase B2b)
            val s = subsOf(p.name)
            if (s.isNotBlank()) append(" · ").append(label).append(": ").append(s)
            appendLine()
        }
        return buildString {
            appendLine("GOAL AREAS (results / projects map here):")
            fw.goalAreas.forEach { line(it, "projects") }
            appendLine("BEHAVIOURS / COMPETENCIES (tag work that demonstrates these):")
            fw.behaviours.forEach { line(it, "focus areas") }
            if (fw.development.isNotEmpty()) {
                appendLine("DEVELOPMENT (optional):")
                fw.development.forEach { line(it, "focus areas") }
            }
        }.trim()
    }
}
