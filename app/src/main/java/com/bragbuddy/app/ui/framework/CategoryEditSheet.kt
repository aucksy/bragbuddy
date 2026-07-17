package com.bragbuddy.app.ui.framework

import android.net.Uri
import com.bragbuddy.app.ui.common.LocalSnackbarController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.common.LocalBottomBarInset
import com.bragbuddy.app.ui.common.rememberDiscardGuard
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import java.io.File

/** Local editable project row. [baseName]/[baseSummary] track the last-saved values so a row can show
 *  a live "unsaved" state and reset after its own Save. [key] is a stable dictation/scan target. */
private class ProjRowState(val key: Int, initialId: Long?, initialName: String, initialSummary: String) {
    var id by mutableStateOf(initialId)
    var name by mutableStateOf(initialName)
    var summary by mutableStateOf(initialSummary)
    var baseName by mutableStateOf(initialName)
    var baseSummary by mutableStateOf(initialSummary)
    var confirmSave by mutableStateOf(false) // deferred "save this detail" confirm for this row
    val dirty: Boolean get() = name.trim() != baseName.trim() || summary.trim() != baseSummary.trim()
}

/**
 * Full-screen editor for one category (Design System §3, Phase B2). Fields take **type or scan** — a
 * scan reads a job description / review-criteria document and drops its text into the field (the mic
 * was removed). Editing is **per-item**: the category (name / axis / detail) has its own Save, and each
 * project (name / detail) has its own Save — each with a confirm that names the effect (a category
 * detail feeds the next summary; a project detail feeds future filing). Renaming the category offers a
 * deterministic relabel of already-filed records (handled by the host screen). [pillar] null = adding a
 * new category (name is typed; its detail can be scanned; projects are added after it exists).
 */
@Composable
fun CategoryEditSheet(
    pillar: Pillar?,
    subFolders: List<ProjectEntity>,
    takenNames: Set<String>, // other categories' names (lowercased) — this one can't duplicate them
    viewModel: FrameworkViewModel,
    onClose: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scanEnabled by viewModel.scanEnabled.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val adding = pillar == null

    var name by remember(pillar?.id) { mutableStateOf(pillar?.name ?: "") }
    var detail by remember(pillar?.id) { mutableStateOf(pillar?.blurb ?: "") }
    var kind by remember(pillar?.id) { mutableStateOf(pillar?.kind ?: PillarKind.GOAL_AREA) }
    // Last-saved category baseline (also the live category name projects attach to). Updated on save.
    var baseName by remember(pillar?.id) { mutableStateOf(pillar?.name ?: "") }
    var baseDetail by remember(pillar?.id) { mutableStateOf(pillar?.blurb ?: "") }
    var baseKind by remember(pillar?.id) { mutableStateOf(pillar?.kind ?: PillarKind.GOAL_AREA) }

    var nextKey by remember(pillar?.id) { mutableStateOf(subFolders.size + 1) }
    val rows = remember(pillar?.id) {
        mutableStateListOf<ProjRowState>().apply {
            subFolders.forEachIndexed { i, p -> add(ProjRowState(i, p.id, p.name, p.description.orEmpty())) }
        }
    }

    // Which field a scan targets: "category" or "proj-<key>". Kept in plain `remember` — the SAME
    // (non-persisted) lifecycle as the editable fields/rows below, so a process-death recreation resets
    // the whole editor together and a restored scan can never land in a row that no longer exists.
    var activeScanTarget by remember(pillar?.id) { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember(pillar?.id) { mutableStateOf<Uri?>(null) }
    var scanSourceChooser by remember { mutableStateOf(false) }

    // Image acquisition — gallery (no permission) + camera (FileProvider Uri; no CAMERA permission).
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onScanImage(uri) else activeScanTarget = null
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val u = pendingPhotoUri
        pendingPhotoUri = null
        if (success && u != null) viewModel.onScanImage(u) else activeScanTarget = null
    }
    fun launchCamera() {
        val uri = runCatching {
            val dir = File(context.cacheDir, "capture_images").apply { mkdirs() }
            val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri == null) {
            activeScanTarget = null
            snackbar.show("Couldn't open the camera — choose an image instead")
            return
        }
        pendingPhotoUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure {
            pendingPhotoUri = null; activeScanTarget = null
            snackbar.show("No camera available — choose an image instead")
        }
    }

    // A completed scan → append into the active field; an error → toast, leave the field unchanged.
    LaunchedEffect(Unit) {
        viewModel.scanText.collect { text ->
            when (val t = activeScanTarget) {
                "category" -> detail = appendText(detail, text)
                else -> if (t != null && t.startsWith("proj-")) {
                    val k = t.removePrefix("proj-").toIntOrNull()
                    rows.firstOrNull { it.key == k }?.let { it.summary = appendText(it.summary, text) }
                }
            }
            activeScanTarget = null
        }
    }
    LaunchedEffect(Unit) {
        viewModel.scanError.collect { msg ->
            activeScanTarget = null
            snackbar.show(msg)
        }
    }

    fun startScan(target: String) {
        if (scanState != ScanState.IDLE) return
        activeScanTarget = target
        scanSourceChooser = true
    }

    val nameTaken = name.isNotBlank() && takenNames.contains(name.trim().lowercase())
    val projectNames = rows.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }
    val dupProject = projectNames.size != projectNames.toSet().size

    // Block every Save while a scan is in flight — a save (or add-then-close) mid-OCR would drop the
    // result, which is delivered on a replay=0 SharedFlow only while this sheet is collecting.
    val scanBusy = scanState != ScanState.IDLE
    val categoryDirty = if (adding) name.isNotBlank()
        else name.trim() != baseName.trim() || detail.trim() != baseDetail.trim() || kind != baseKind
    val canSaveCategory = name.isNotBlank() && !nameTaken && categoryDirty && !scanBusy

    // Confirm state: the category save (with an adaptive message).
    var confirmCategory by remember { mutableStateOf(false) }

    // Guard the close ✕ + system Back against losing unsaved category or project edits (each item is
    // saved on its own, so an accidental close/Back would drop anything typed-or-scanned but not yet Saved).
    val editorDirty = categoryDirty || rows.any { it.dirty }
    val requestClose = rememberDiscardGuard(
        dirty = editorDirty,
        onDismiss = onClose,
        message = "You have unsaved framework changes that weren't Saved. Discard them?",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top bar — close only; each item is saved on its own.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestClose) {
                Icon(Icons.Outlined.Close, "Close", tint = palette.text2)
            }
            Text(
                if (adding) "Add category" else "Edit category",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(Spacing.s2))
            FieldLabel("CATEGORY NAME", palette)
            PlainField(name, { name = it }, "e.g. Performance Goals", palette)
            if (nameTaken) {
                Spacer(Modifier.height(Spacing.s1))
                Text("A category with this name already exists.", style = MaterialTheme.typography.labelSmall, color = palette.extraInk)
            }

            Spacer(Modifier.height(Spacing.s4))
            FieldLabel("AXIS", palette)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).padding(4.dp)) {
                KindSeg("Goal", kind == PillarKind.GOAL_AREA, { kind = PillarKind.GOAL_AREA }, Modifier.weight(1f), palette)
                KindSeg("Behaviour", kind == PillarKind.BEHAVIOUR, { kind = PillarKind.BEHAVIOUR }, Modifier.weight(1f), palette)
                KindSeg("Growth", kind == PillarKind.DEVELOPMENT, { kind = PillarKind.DEVELOPMENT }, Modifier.weight(1f), palette)
            }

            Spacer(Modifier.height(Spacing.s4))
            FieldLabel("CATEGORY DETAIL", palette)
            ScanField(
                value = detail, onValueChange = { detail = it },
                placeholder = "What this category covers — type it, or scan a job description / review form.",
                palette = palette, scanEnabled = scanEnabled,
                reading = scanState == ScanState.READING && activeScanTarget == "category",
                scanBusy = scanState != ScanState.IDLE,
                onScan = { startScan("category") },
            )
            Spacer(Modifier.height(Spacing.s3))
            SaveButton("Save category", enabled = canSaveCategory, palette = palette) {
                if (adding) { viewModel.addCategory(name, detail, kind); onClose() } else confirmCategory = true
            }

            if (!adding) {
                Spacer(Modifier.height(Spacing.s6))
                FieldLabel(if (kind == PillarKind.GOAL_AREA) "PROJECTS" else "FOCUS AREAS", palette)
                if (dupProject) {
                    Text("Two projects can't share the same name.", style = MaterialTheme.typography.labelSmall, color = palette.extraInk)
                    Spacer(Modifier.height(Spacing.s2))
                }

                rows.forEach { row ->
                    val target = "proj-${row.key}"
                    var confirmDelete by remember(row.key) { mutableStateOf(false) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.s3)
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
                            .padding(Spacing.card),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                PlainField(row.name, { row.name = it }, "Project name", palette)
                            }
                            IconButton(onClick = { if (row.id == null) rows.remove(row) else confirmDelete = true }) {
                                Icon(Icons.Outlined.Close, "Remove project", tint = palette.text3, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(Spacing.s2))
                        ScanField(
                            value = row.summary, onValueChange = { row.summary = it },
                            placeholder = "How is this project judged? e.g. faster processing, adoption %, fewer errors — type or scan.",
                            palette = palette, scanEnabled = scanEnabled,
                            reading = scanState == ScanState.READING && activeScanTarget == target,
                            scanBusy = scanState != ScanState.IDLE,
                            onScan = { startScan(target) },
                        )
                        Spacer(Modifier.height(Spacing.s1))
                        Text(
                            "The more you note how success is measured here, the sharper the AI's impact prompts on your future entries. Keep it generic — skip confidential names, clients, or exact figures.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.text3,
                        )
                        Spacer(Modifier.height(Spacing.s2))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            val rowDup = row.name.trim().isNotEmpty() &&
                                rows.count { it.name.trim().equals(row.name.trim(), ignoreCase = true) } > 1
                            SaveChip(enabled = row.name.trim().isNotEmpty() && row.dirty && !rowDup && !scanBusy, palette = palette) {
                                // A detail change on an EXISTING project is confirmed (it affects future
                                // filing); a new project or a name-only edit just saves.
                                if (row.id != null && row.summary.trim() != row.baseSummary.trim()) {
                                    row.confirmSave = true
                                } else {
                                    doSaveProject(viewModel, row, baseName, snackbar::show)
                                }
                            }
                        }
                    }

                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    row.id?.let { viewModel.deleteProject(it) }
                                    rows.remove(row); confirmDelete = false
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                            title = { Text("Delete “${row.name}”?", color = palette.text1) },
                            text = { Text("Removes this project. Entries already filed to it stay in your record.", color = palette.text3) },
                            containerColor = palette.surface,
                        )
                    }

                    // Deferred confirm for saving an existing project's changed detail.
                    if (row.confirmSave) {
                        AlertDialog(
                            onDismissRequest = { row.confirmSave = false },
                            confirmButton = {
                                TextButton(onClick = { row.confirmSave = false; doSaveProject(viewModel, row, baseName, snackbar::show) }) {
                                    Text("Save")
                                }
                            },
                            dismissButton = { TextButton(onClick = { row.confirmSave = false }) { Text("Cancel") } },
                            title = { Text("Save project detail?", color = palette.text1) },
                            text = {
                                Text(
                                    "Future entries filed here will use this detail. Records already filed to this project stay exactly as they are.",
                                    color = palette.text3,
                                )
                            },
                            containerColor = palette.surface,
                        )
                    }
                }

                AddRowButton("Add project", palette, enabled = true) {
                    rows.add(ProjRowState(nextKey, null, "", "")); nextKey++
                }
            }
            // This editor fills the screen edge-to-edge with no navigationBarsPadding, UNDER
            // MainScaffold's bottom bar/FAB (drawn on top of the Framework tab) — so the scroll tail
            // must clear BOTH the system nav bar and the app bar, or the last control ("Add project" /
            // "Save category") is unreachable. The bar term is 0.dp in onboarding, which embeds this
            // screen with no bar; the system inset still applies there.
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Spacer(Modifier.height(Spacing.s8 + bottomInset + LocalBottomBarInset.current))
        }
    }

    // Category save confirm (adaptive message).
    if (confirmCategory) {
        val renamed = name.trim() != baseName.trim()
        val detailChanged = detail.trim() != baseDetail.trim()
        val hasChildren = subFolders.isNotEmpty()
        val childWord = if (kind == PillarKind.GOAL_AREA) "projects" else "focus areas"
        val msg = buildString {
            if (detailChanged) append("Your next summary will use this updated detail. ")
            if (renamed) {
                append("Renaming “${baseName}” → “${name.trim()}”.")
                if (hasChildren) append(" Its $childWord come along.")
                append(" If entries are already filed under “${baseName}”, I'll ask whether to move them (and their summaries) to “${name.trim()}” too.")
            }
        }.trim().ifBlank { "Save changes to this category?" }
        AlertDialog(
            onDismissRequest = { confirmCategory = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmCategory = false
                    pillar?.let { viewModel.saveCategory(it.id, name, detail, kind) }
                    baseName = name.trim(); baseDetail = detail.trim(); baseKind = kind
                    snackbar.show("Category saved")
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { confirmCategory = false }) { Text("Cancel") } },
            title = { Text("Save category?", color = palette.text1) },
            text = { Text(msg, color = palette.text3) },
            containerColor = palette.surface,
        )
    }

    // Scan source chooser (camera / gallery).
    if (scanSourceChooser) {
        AlertDialog(
            onDismissRequest = { scanSourceChooser = false; activeScanTarget = null },
            confirmButton = {
                TextButton(onClick = { scanSourceChooser = false; launchCamera() }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scanSourceChooser = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
            },
            title = { Text("Scan a document", color = palette.text1) },
            text = { Text("Take a photo of your job description / review criteria, or pick an image — I'll read the text into the field.", color = palette.text3) },
            containerColor = palette.surface,
        )
    }
}

private fun doSaveProject(viewModel: FrameworkViewModel, row: ProjRowState, category: String, showMessage: (String) -> Unit) {
    val creating = row.id == null
    // Snapshot what was REQUESTED. The callback lands after a DB round-trip, and `row.name` is live
    // state the user can keep typing into — comparing the stored name against whatever the field holds
    // by then reports a perfectly good save as rejected.
    val requested = row.name.trim()
    val requestedSummary = row.summary
    viewModel.saveProject(row.id, requested, requestedSummary, category) { newId, storedName ->
        // A create that hit the (name, goalArea) unique index returns a non-positive id — nothing was
        // saved, so keep the row dirty and say so rather than falsely showing it as saved.
        //
        // An UPDATE can be rejected the same way and just as silently (`UPDATE OR IGNORE`), which this
        // used to miss entirely: it reported "Project saved" and set `baseName` to the name the user
        // typed, so the row's idea of itself drifted away from the database. `storedName` is what the row
        // actually holds, so a rejected rename now says so and the baseline stays true. (The VM no longer
        // depends on `baseName` for its remap gate either — it reads both names from the DB — but a UI
        // that lies about a save is its own bug.)
        val rejected = if (creating) newId <= 0L else storedName == null ||
            !storedName.equals(requested, ignoreCase = true)
        if (rejected) {
            showMessage("Couldn't save — a project with that name already exists here.")
        } else {
            if (creating) row.id = newId
            row.baseName = storedName ?: requested
            row.baseSummary = requestedSummary
            showMessage("Project saved")
        }
    }
}

private fun appendText(base: String, extra: String): String {
    val add = extra.trim()
    if (add.isBlank()) return base
    val b = base.trim()
    return if (b.isEmpty()) add else "$b $add"
}

@Composable
private fun FieldLabel(text: String, palette: BragPalette) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(Spacing.s2))
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, placeholder: String, palette: BragPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radii.md))
            .border(1.dp, palette.border, RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp)),
            cursorBrush = SolidColor(palette.primary),
        )
    }
}

/** A detail field that takes **type or scan** — the scan button reads a document into the field. */
@Composable
private fun ScanField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: BragPalette,
    scanEnabled: Boolean,
    reading: Boolean,
    scanBusy: Boolean,
    onScan: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, palette.primary.copy(alpha = if (reading) 0.6f else 0.35f), RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
            BasicTextField(
                value = value, onValueChange = onValueChange,
                // Cap growth so a long detail scrolls inside the field instead of ballooning the row.
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp, lineHeight = 20.sp)),
                cursorBrush = SolidColor(palette.primary),
            )
        }
        if (scanEnabled) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reading) {
                    CircularProgressIndicator(color = palette.primary, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reading…", style = MaterialTheme.typography.labelSmall, color = palette.text3)
                } else {
                    Spacer(Modifier.weight(1f))
                    val enabled = !scanBusy
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (enabled) palette.primarySoft else palette.surface2)
                            .clickable(enabled = enabled, onClick = onScan)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PhotoCamera, "Scan a document", tint = if (enabled) palette.primary else palette.text3, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan", style = MaterialTheme.typography.labelMedium, color = if (enabled) palette.primary else palette.text3, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(text: String, enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.primary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun SaveChip(enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text("Save", color = if (enabled) Color.White else palette.text3, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun KindSeg(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, palette: BragPalette) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) palette.surface else Color.Transparent).clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) palette.primary else palette.text3, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddRowButton(text: String, palette: BragPalette, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .border(1.5.dp, palette.primary.copy(alpha = if (enabled) 0.4f else 0.2f), RoundedCornerShape(Radii.lg))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+ $text", style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}
