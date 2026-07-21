package com.bragbuddy.app.ui.summary

import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import com.bragbuddy.app.data.rollup.DeliverableFact

/**
 * Serialises a generated [SummaryResult] into **clean plain text** for Word / Google Docs — the
 * copy-paste payoff (Build Brief · Design System §5B "Export, clean for Word & Docs").
 *
 * ⭐ S1 (Structured-Summary arc): a goal area now exports the user's OWN hierarchy —
 * `AREA → project → deliverable → pointers` — as REAL indented headers, replacing the old flat list
 * whose lines carried `[Project ▸ Deliverable]` tags. It reuses the exact grouping functions the
 * screen renders from ([groupAchievementsByProject] / [groupFolderByDeliverable]), so the pasted doc
 * and the screen can never disagree about the shape. Mirrors the living-document exporter's look
 * ([com.bragbuddy.app.ui.home.exportDocument]): UPPERCASE area heading, plain-name sub-headings
 * indented two spaces per level, `• ` bullets, a done deliverable marked `(Done)` (Home's convention),
 * a merged pointer marked `(from N logs)`, routine work as a single counted `×N` line. The
 * reassurance-only "set aside" notes are NOT part of the pasted document. Pure + unit-tested.
 */

private const val INDENT = "  "

/** A bullet at [units] indent levels, with the metric appended when the bullet doesn't already say it
 *  and a `(from N logs)` note when this pointer condenses several log entries (S1). */
private fun pointerLine(bullet: String, metric: String?, count: Int, units: Int): String? {
    val text = bullet.trim()
    if (text.isEmpty()) return null
    val withMetric = if (!metric.isNullOrBlank() && !text.contains(metric.trim(), ignoreCase = true)) {
        "$text — ${metric.trim()}"
    } else {
        text
    }
    val withCount = if (count > 1) "$withMetric  (from $count logs)" else withMetric
    return INDENT.repeat(units) + "• " + withCount
}

/** A plain evidence/development bullet (no metric, no count) — behaviours and L&G keep their pre-S1 shape. */
private fun bulletLine(text: String, units: Int = 1): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return INDENT.repeat(units) + "• " + t
}

private fun rolledUpLine(bullet: String, routineType: String, count: Int): String? {
    val text = bullet.trim().ifBlank { routineType.trim() }
    if (text.isEmpty()) return null
    return "$INDENT• " + text + if (count > 0) "  ×$count" else ""
}

/**
 * One run of achievements (a project folder's items, or a flat area's) as lines: named deliverable
 * groups first — each a real sub-heading (+ `(Done)`) with deeper-indented pointers — then the loose
 * pointers at [baseUnits]. A group whose pointers are all blank is skipped rather than pasted as a
 * bare heading (Home's rule: an appraisal document never carries an empty section).
 */
private fun exportItems(
    items: List<IndexedAchievement>,
    baseUnits: Int,
    areaName: String,
    project: String?,
    facts: List<DeliverableFact>,
): List<String> {
    val lines = mutableListOf<String>()
    val groups = groupFolderByDeliverable(items)
    if (groups == null) {
        items.forEach { ia ->
            pointerLine(ia.achievement.bullet, ia.achievement.metric, ia.achievement.count, baseUnits)?.let { lines += it }
        }
        return lines
    }
    groups.forEach { g ->
        if (g.name != null) {
            val bullets = g.items.mapNotNull { pointerLine(it.achievement.bullet, it.achievement.metric, it.achievement.count, baseUnits + 1) }
            if (bullets.isNotEmpty()) {
                val projectForFact = project ?: g.items.first().achievement.project
                val done = if (deliverableDone(facts, areaName, projectForFact, g.name)) " (Done)" else ""
                lines += INDENT.repeat(baseUnits) + g.name + done
                lines += bullets
            }
        } else {
            lines += g.items.mapNotNull { pointerLine(it.achievement.bullet, it.achievement.metric, it.achievement.count, baseUnits) }
        }
    }
    return lines
}

/**
 * One goal area as text: `NAME` heading, then the hierarchy — each named project a sub-heading with
 * its deliverable groups and loose pointers under it, no-project pointers plainly at the area level
 * after the named projects (the S-arc rule of shape), then rolled-up routine lines.
 */
fun exportGoalArea(area: SummaryGoalArea, deliverables: List<DeliverableFact> = emptyList()): String {
    val lines = mutableListOf<String>()
    val folders = groupAchievementsByProject(area.achievements)
    if (folders == null) {
        // Nothing named — the area is loose work, exported plainly (the pre-S1 flat shape).
        lines += exportItems(
            area.achievements.mapIndexed { i, a -> IndexedAchievement(i, a) },
            baseUnits = 1, areaName = area.name, project = null, facts = deliverables,
        )
    } else {
        folders.forEach { folder ->
            if (folder.isOutside) {
                // "No-project wins list plainly under the goal area" — no synthetic folder heading in
                // the doc a manager reads (the screen's labelled bucket is a navigation aid, not shape).
                lines += exportItems(folder.items, baseUnits = 1, areaName = area.name, project = null, facts = deliverables)
            } else {
                val body = exportItems(folder.items, baseUnits = 2, areaName = area.name, project = folder.name, facts = deliverables)
                if (body.isNotEmpty()) {
                    lines += INDENT + folder.name
                    lines += body
                }
            }
        }
    }
    lines += area.rolledUp.mapNotNull { rolledUpLine(it.bullet, it.routineType, it.count) }
    val sb = StringBuilder(area.name.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/**
 * One behaviour as text: `NAME` heading, then its category-level evidence bullets, then (item 4)
 * each nested competency as an indented sub-heading with its own bullets. A flat behaviour (no
 * competencies) exports exactly as before. (Evidence stays untagged in S1 — the `[Project ▸
 * Deliverable]` tags on leadership evidence arrive with the S3 prompt phase.)
 */
fun exportBehaviour(b: SummaryBehaviour): String {
    val sb = StringBuilder(b.name.uppercase())
    // Category-level bullets + any evidence from an UNNAMED competency (a model glitch) folded up, so
    // it's never lost and never rendered under an empty sub-heading.
    val looseEvidence = b.evidence + b.competencies.filter { it.name.isBlank() }.flatMap { it.evidence }
    looseEvidence.mapNotNull { bulletLine(it) }.forEach { sb.append("\n").append(it) }
    b.competencies.filter { it.name.isNotBlank() }.forEach { comp ->
        val evLines = comp.evidence.mapNotNull { bulletLine(it, units = 2) }
        if (evLines.isNotEmpty()) {
            sb.append("\n").append(INDENT).append(comp.name.trim())
            evLines.forEach { sb.append("\n").append(it) }
        }
    }
    return sb.toString()
}

/** The development block, or null if empty. */
private fun exportDevelopment(development: List<String>): String? {
    val lines = development.mapNotNull { bulletLine(it) }
    if (lines.isEmpty()) return null
    return "LEARNING & GROWTH\n" + lines.joinToString("\n")
}

/** The whole generated summary as clean text. [title] (e.g. "Year-end summary") heads the document.
 *  [deliverables] supplies each deliverable's live Done state for the `(Done)` marks. */
fun exportSummary(result: SummaryResult, title: String, deliverables: List<DeliverableFact> = emptyList()): String {
    val blocks = mutableListOf<String>()
    if (title.isNotBlank()) blocks.add(title.trim().uppercase())
    result.summary.goalAreas.forEach { area ->
        if (area.achievements.isNotEmpty() || area.rolledUp.isNotEmpty()) blocks.add(exportGoalArea(area, deliverables))
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
