package com.bragbuddy.app.ui.home

import com.bragbuddy.app.data.local.EntryEntity

/**
 * Serialises the living document (or one section / folder) into **clean plain text** that pastes
 * neatly into Word / Google Docs (Build Brief § "the copy-paste moment is the payoff"). No Markdown
 * symbols, no stray characters — just an UPPERCASE pillar heading, project sub-headings, and `•`
 * bullets. Pure + unit-tested; the entry's cleaned bullet is used (raw transcript as a fallback so a
 * not-yet-processed entry still exports something), with a trailing `[Standout]` for ★ entries.
 */

private fun entryLine(e: EntryEntity, indent: String = "  "): String? {
    val text = (e.bullet?.takeIf { it.isNotBlank() } ?: e.rawTranscript).trim()
    if (text.isEmpty()) return null
    return "$indent• " + text + if (e.isExtra) "  [Standout]" else ""
}

/**
 * One project's body — its deliverable groups, then the loose bullets, then the done groups: the same
 * order the screen renders, so a paste matches what the user is looking at.
 *
 * A deliverable with no exportable bullet is skipped rather than pasted as a bare heading (mirrors the
 * pillar/project rule above it) — an appraisal document should never carry an empty section. A **done**
 * one is marked, because that's the part a reader needs: it shipped.
 */
private fun projectBody(p: ProjectBullets): List<String> {
    val lines = mutableListOf<String>()
    for (g in p.deliverables.filterNot { it.done }) {
        val bullets = g.entries.mapNotNull { entryLine(it, "    ") }
        if (bullets.isEmpty()) continue
        lines += "  ${g.name}"
        lines += bullets
    }
    lines += p.loose.mapNotNull { entryLine(it) }
    for (g in p.deliverables.filter { it.done }) {
        val bullets = g.entries.mapNotNull { entryLine(it, "    ") }
        if (bullets.isEmpty()) continue
        lines += "  ${g.name} (Done)"
        lines += bullets
    }
    return lines
}

/** A goal/growth pillar as text: `PILLAR` heading, then each non-empty project with its bullets. */
fun exportGoalBlock(pillarName: String, projects: List<ProjectBullets>): String {
    val sb = StringBuilder(pillarName.uppercase())
    for (p in projects) {
        val lines = projectBody(p)
        if (lines.isEmpty()) continue
        sb.append("\n\n")
        sb.append(if (p.isOutside) OUTSIDE_PROJECT_LABEL else p.name)
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/** A behaviour pillar as text: `PILLAR` heading, then the bullets that evidence it. */
fun exportBehaviourBlock(pillarName: String, evidence: List<EntryEntity>): String {
    val lines = evidence.mapNotNull { entryLine(it) }
    val sb = StringBuilder(pillarName.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/** A single folder as text (the "See all" folder screen's copy): `FOLDER` heading + its bullets. */
fun exportFolderBlock(project: ProjectBullets): String {
    val name = if (project.isOutside) OUTSIDE_PROJECT_LABEL else project.name
    val lines = projectBody(project)
    val sb = StringBuilder(name.uppercase())
    if (lines.isNotEmpty()) {
        sb.append("\n")
        sb.append(lines.joinToString("\n"))
    }
    return sb.toString()
}

/** The whole document (Home "Copy everything"): every pillar that has at least one bullet. */
fun exportDocument(doc: HomeDoc): String {
    val blocks = mutableListOf<String>()
    doc.goals.forEach { s ->
        if (s.projects.any { p -> p.entries.any { entryLine(it) != null } }) {
            blocks.add(exportGoalBlock(s.pillar.name, s.projects))
        }
    }
    doc.behaviours.forEach { s ->
        if (s.evidence.any { entryLine(it) != null }) {
            blocks.add(exportBehaviourBlock(s.pillar.name, s.evidence))
        }
    }
    return blocks.joinToString("\n\n")
}
