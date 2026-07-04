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

private fun achievementLine(bullet: String, project: String?, metric: String?): String? {
    val text = bullet.trim()
    if (text.isEmpty()) return null
    val withMetric = if (!metric.isNullOrBlank() && !text.contains(metric.trim(), ignoreCase = true)) {
        "$text — ${metric.trim()}"
    } else {
        text
    }
    val proj = project?.trim()?.takeIf { it.isNotBlank() }
    return "  • " + withMetric + if (proj != null) "  [$proj]" else ""
}

private fun rolledUpLine(bullet: String, routineType: String, count: Int): String? {
    val text = bullet.trim().ifBlank { routineType.trim() }
    if (text.isEmpty()) return null
    return "  • " + text + if (count > 0) "  ×$count" else ""
}

/** One goal area as text: `NAME` heading, then achievements, then rolled-up lines. */
fun exportGoalArea(area: SummaryGoalArea): String {
    val lines = area.achievements.mapNotNull { achievementLine(it.bullet, it.project, it.metric) } +
        area.rolledUp.mapNotNull { rolledUpLine(it.bullet, it.routineType, it.count) }
    val sb = StringBuilder(area.name.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/** One behaviour as text: `NAME` heading, then its evidence bullets. */
fun exportBehaviour(b: SummaryBehaviour): String {
    val lines = b.evidence.mapNotNull { achievementLine(it, null, null) }
    val sb = StringBuilder(b.name.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
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
        if (b.evidence.isNotEmpty()) blocks.add(exportBehaviour(b))
    }
    exportDevelopment(result.summary.development)?.let { blocks.add(it) }
    return blocks.joinToString("\n\n")
}
