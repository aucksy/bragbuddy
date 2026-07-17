package com.bragbuddy.app.ui.summary

import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult

/**
 * Serialises a generated [SummaryResult] into **clean plain text** for Word / Google Docs — the
 * copy-paste payoff (Build Brief · Design System §5B "Export, clean for Word & Docs"). Mirrors the
 * living-document exporter's look ([com.bragbuddy.app.ui.home.exportDocument]): an UPPERCASE pillar
 * heading, then `  • ` bullets, routine work as a single counted `×N` line, behaviour evidence, and
 * development. The reassurance-only "set aside" notes are NOT part of the pasted document. Pure +
 * unit-tested.
 */

private fun achievementLine(
    bullet: String,
    project: String?,
    metric: String?,
    deliverable: String? = null,
): String? {
    val text = bullet.trim()
    if (text.isEmpty()) return null
    val withMetric = if (!metric.isNullOrBlank() && !text.contains(metric.trim(), ignoreCase = true)) {
        "$text — ${metric.trim()}"
    } else {
        text
    }
    val proj = project?.trim()?.takeIf { it.isNotBlank() } ?: return "  • $withMetric"
    // The deliverable rides INSIDE the project's tag (v0.34.0), never on its own: a deliverable name is
    // only meaningful under its project ("Phase 1" alone says nothing to a manager), and it is dropped
    // entirely when there's no project to qualify it rather than pasted as a tag nobody can place.
    val del = deliverable?.trim()?.takeIf { it.isNotBlank() }
    val tag = if (del != null) "$proj ▸ $del" else proj
    return "  • $withMetric  [$tag]"
}

private fun rolledUpLine(bullet: String, routineType: String, count: Int): String? {
    val text = bullet.trim().ifBlank { routineType.trim() }
    if (text.isEmpty()) return null
    return "  • " + text + if (count > 0) "  ×$count" else ""
}

/** One goal area as text: `NAME` heading, then achievements, then rolled-up lines. */
fun exportGoalArea(area: SummaryGoalArea): String {
    val lines = area.achievements.mapNotNull { achievementLine(it.bullet, it.project, it.metric, it.deliverable) } +
        area.rolledUp.mapNotNull { rolledUpLine(it.bullet, it.routineType, it.count) }
    val sb = StringBuilder(area.name.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/** A nested competency evidence bullet — deeper-indented so it sits under its competency sub-head. */
private fun nestedEvidenceLine(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return "    • " + t
}

/**
 * One behaviour as text: `NAME` heading, then its category-level evidence bullets, then (item 4)
 * each nested competency as an indented sub-heading with its own bullets. A flat behaviour (no
 * competencies) exports exactly as before.
 */
fun exportBehaviour(b: SummaryBehaviour): String {
    val sb = StringBuilder(b.name.uppercase())
    // Category-level bullets + any evidence from an UNNAMED competency (a model glitch) folded up, so
    // it's never lost and never rendered under an empty sub-heading.
    val looseEvidence = b.evidence + b.competencies.filter { it.name.isBlank() }.flatMap { it.evidence }
    looseEvidence.mapNotNull { achievementLine(it, null, null) }.forEach { sb.append("\n").append(it) }
    b.competencies.filter { it.name.isNotBlank() }.forEach { comp ->
        val evLines = comp.evidence.mapNotNull { nestedEvidenceLine(it) }
        if (evLines.isNotEmpty()) {
            sb.append("\n  ").append(comp.name.trim())
            evLines.forEach { sb.append("\n").append(it) }
        }
    }
    return sb.toString()
}

/** The development block, or null if empty. */
private fun exportDevelopment(development: List<String>): String? {
    val lines = development.mapNotNull { achievementLine(it, null, null) }
    if (lines.isEmpty()) return null
    return "LEARNING & GROWTH\n" + lines.joinToString("\n")
}

/** The whole generated summary as clean text. [title] (e.g. "Year-end summary") heads the document. */
fun exportSummary(result: SummaryResult, title: String): String {
    val blocks = mutableListOf<String>()
    if (title.isNotBlank()) blocks.add(title.trim().uppercase())
    result.summary.goalAreas.forEach { area ->
        if (area.achievements.isNotEmpty() || area.rolledUp.isNotEmpty()) blocks.add(exportGoalArea(area))
    }
    result.summary.behaviours.forEach { b ->
        // Mirror the on-screen render guard: a category whose evidence lives ENTIRELY in nested
        // competencies (item 4's Leadership case) has an empty top-level evidence[] but must still be
        // exported — otherwise the whole-summary Copy silently drops the section.
        if (b.evidence.isNotEmpty() || b.competencies.any { it.evidence.isNotEmpty() }) blocks.add(exportBehaviour(b))
    }
    exportDevelopment(result.summary.development)?.let { blocks.add(it) }
    return blocks.joinToString("\n\n")
}
