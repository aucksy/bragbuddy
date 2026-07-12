package com.bragbuddy.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bragbuddy.app.ui.capture.CaptureLauncher
import com.bragbuddy.app.ui.framework.FrameworkScreen
import com.bragbuddy.app.ui.legal.PrivacyContent
import com.bragbuddy.app.ui.role.RoleInput
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.PillarRamp
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * First-run onboarding wizard: **Welcome → Privacy (hard gate) → Recover → Role → Framework → Rehearse**.
 * Guided but skippable — role, framework and the rehearsal can be skipped (framework then keeps
 * `Framework.DEFAULT`); privacy is required. The final **Rehearse** step (M2) has the user log one real
 * win and watch it file live into the framework they just built, landing on a made-real "YOUR RECORD ·
 * READY" card — the aha moment. Adopts the Design System §2 tone (warm intro, dot progress, pill CTA, the amber "no company
 * name" reassurance) but replaces the design's AI voice-refine framework step with the real Type + Scan
 * editor ([FrameworkScreen]) per the 2026-07-07 reshape — no AI ever rewrites the framework.
 *
 * When [reacceptOnly] is true the flow collapses to just the privacy card (a material privacy-version
 * bump for an already-onboarded user): accept re-stamps the version and finishes.
 */
private const val TOTAL_STEPS = 6

@Composable
fun OnboardingScreen(
    reacceptOnly: Boolean,
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    // Navigate away only after the finish write is durably persisted (the VM flips this true after the
    // atomic settings write — navigating earlier would cancel that write with the nav pop).
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onFinished() }

    if (reacceptOnly) {
        PrivacyStep(dotIndex = -1, onAccept = { viewModel.finishReaccept() })
        return
    }

    val initialRole by viewModel.jobRole.collectAsStateWithLifecycle()
    val driveState by viewModel.driveState.collectAsStateWithLifecycle()
    val driveSignIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onDriveSignInResult(result.data)
    }
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> WelcomeStep(onNext = { step = 1 })
        1 -> PrivacyStep(dotIndex = 1, onAccept = { viewModel.acceptPrivacy(); step = 2 })
        2 -> RecoverStep(
            drive = driveState,
            configured = viewModel.driveConfigured,
            onConnect = { driveSignIn.launch(viewModel.driveSignInIntent()) },
            onRestore = { viewModel.restoreFromDriveAndFinish() }, // success → `finished` → Home
            onSkip = { viewModel.declineRestore(); step = 3 },
        )
        3 -> RoleStep(
            initialRole = initialRole,
            onContinue = { role -> viewModel.saveRole(role); step = 4 },
            onSkip = { viewModel.skipRole(); step = 4 },
        )
        4 -> FrameworkStep(onNext = { step = 5 })
        else -> {
            val rehearsal by viewModel.rehearsal.collectAsStateWithLifecycle()
            RehearseStep(
                rehearsal = rehearsal,
                onBegin = { viewModel.beginRehearsal() },
                onFinish = { viewModel.finish() },
            )
        }
    }
}

// ---------------- Steps ----------------

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            // A calm echo of "your record, ready" (Design §2 · The promise).
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
                    .padding(Spacing.card),
            ) {
                Text(
                    "YOUR RECORD · READY",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.text3,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.s3))
                MockPillarRow(PillarRamp[0].solid, "Performance Goals", palette)
                Spacer(Modifier.height(Spacing.s2))
                MockPillarRow(PillarRamp[1].solid, "Leadership & Behaviours", palette)
                Spacer(Modifier.height(Spacing.s2))
                MockPillarRow(PillarRamp[2].solid, "Learning & Growth", palette)
                Spacer(Modifier.height(Spacing.s3))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.positiveSoft)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, tint = palette.positive, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Copy for review", style = MaterialTheme.typography.labelSmall, color = palette.positive, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(Spacing.s5))
            Text(
                "Never scramble at review time again.",
                style = MaterialTheme.typography.displaySmall,
                color = palette.text1,
            )
            Spacer(Modifier.height(Spacing.s3))
            Text(
                "A few seconds a day — speak, type, or scan — and BragBuddy keeps an always-ready, review-ready record of your wins.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text2,
            )
            Spacer(Modifier.height(Spacing.s4))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                CaptureHintChip(Icons.Outlined.Mic, "Speak", palette)
                CaptureHintChip(Icons.Outlined.Keyboard, "Type", palette)
                CaptureHintChip(Icons.Outlined.PhotoCamera, "Scan", palette)
            }
        }

        DotProgress(index = 0)
        Spacer(Modifier.height(Spacing.s4))
        PrimaryPill("Get started", palette, onClick = onNext)
        Spacer(Modifier.height(Spacing.s4))
    }
}

@Composable
private fun PrivacyStep(dotIndex: Int, onAccept: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Shared body — the concise, de-keyed onboarding summary (Phase 3). The full policy stays in
        // Settings → Privacy; acceptance still binds the full terms.
        PrivacyContent(Modifier.weight(1f), concise = true)

        // Pinned accept bar — the hard gate.
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s3)) {
                Text(
                    "By continuing you agree to these principles and terms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s3))
                if (dotIndex >= 0) {
                    DotProgress(index = dotIndex)
                    Spacer(Modifier.height(Spacing.s3))
                }
                PrimaryPill("Accept & continue", palette, onClick = onAccept)
            }
        }
    }
}

@Composable
private fun RoleStep(initialRole: String, onContinue: (String) -> Unit, onSkip: () -> Unit) {
    val palette = BragBuddyTheme.palette
    var draft by rememberSaveable { mutableStateOf("") }
    // Seed from an existing saved role (a forced re-onboard) without ever clobbering what's being typed.
    LaunchedEffect(initialRole) { if (draft.isBlank() && initialRole.isNotBlank()) draft = initialRole }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Text("What's your role?", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
            Spacer(Modifier.height(Spacing.s3))
            Text(
                "It helps the AI judge what's core versus standout for you. Free text — and never your company name.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text2,
            )
            Spacer(Modifier.height(Spacing.s4))
            RoleInput(
                value = draft,
                onValueChange = { draft = it },
                palette = palette,
                onImeDone = { if (draft.isNotBlank()) onContinue(draft) },
            )
            Spacer(Modifier.height(Spacing.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Product Owner", "Software Engineer", "Designer", "Manager").forEach { ex ->
                    ExampleChip(ex, palette) { draft = ex }
                }
            }
            Spacer(Modifier.height(Spacing.s4))
            // Reassurance the design calls for — no company name, ever.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(palette.extraSoft)
                    .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BragBuddy never asks for your company name — the whole record stays anonymous.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.extraInk,
                )
            }
        }

        DotProgress(index = 3)
        Spacer(Modifier.height(Spacing.s4))
        PrimaryPill(if (draft.isBlank()) "Continue" else "Save & continue", palette, onClick = {
            if (draft.isBlank()) onSkip() else onContinue(draft)
        })
        Spacer(Modifier.height(Spacing.s2))
        SecondaryText("Skip for now", palette, onClick = onSkip)
        Spacer(Modifier.height(Spacing.s4))
    }
}

@Composable
private fun FrameworkStep(onNext: () -> Unit) {
    val palette = BragBuddyTheme.palette
    // Hide the finish bar while a full-screen category/project editor sheet is open, so it can't be
    // tapped through the sheet (which would advance onboarding and drop unsaved editor text). The sheet
    // then covers the whole step; the bar returns when the sheet closes.
    var editingFramework by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(palette.bg)) {
        // The real Type + Scan editor (handles its own status-bar inset, scroll, and header).
        Box(Modifier.weight(1f)) {
            FrameworkScreen(contentBottomPadding = Spacing.s6, reportEditing = { editingFramework = it })
        }
        // Pinned bar (hidden while editing).
        if (!editingFramework) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s3)) {
                    DotProgress(index = 4)
                    Spacer(Modifier.height(Spacing.s3))
                    PrimaryPill("Next", palette, onClick = onNext)
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "Optional — refine this now or anytime in the Framework tab. The AI never rewrites it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The aha rehearsal (M2 · final step): log one real win → watch it file live → land on the made-real
 * "YOUR RECORD · READY" card. Reuses the real capture activity (voice / type / scan) via [CaptureLauncher]
 * so the first win is genuine, then observes it through [OnboardingViewModel.rehearsal]. Fully skippable.
 * Degrades honestly when no AI is configured yet (the win is still saved; it files once AI is set up).
 */
@Composable
private fun RehearseStep(
    rehearsal: OnboardingViewModel.RehearsalUi,
    onBegin: () -> Unit,
    onFinish: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    // Snapshot the baseline the moment the step opens, so only a win logged from here counts.
    LaunchedEffect(Unit) { onBegin() }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            when (val r = rehearsal) {
                is OnboardingViewModel.RehearsalUi.Ready -> {
                    RealRecordCard(bullet = r.bullet, goalArea = r.goalArea, filed = true, palette = palette)
                    Spacer(Modifier.height(Spacing.s5))
                    Text("That's your record, started.", style = MaterialTheme.typography.displaySmall, color = palette.text1)
                    Spacer(Modifier.height(Spacing.s3))
                    Text(
                        "Every win you log lands here, always review-ready. Do this a few seconds a day and review time takes care of itself.",
                        style = MaterialTheme.typography.bodyLarge, color = palette.text2,
                    )
                }
                is OnboardingViewModel.RehearsalUi.Saved -> {
                    RealRecordCard(bullet = r.bullet, goalArea = null, filed = false, palette = palette)
                    Spacer(Modifier.height(Spacing.s5))
                    Text("Saved — your record's started.", style = MaterialTheme.typography.displaySmall, color = palette.text1)
                    Spacer(Modifier.height(Spacing.s3))
                    Text(
                        "It'll file itself into a category as soon as AI is set up. Nothing you log is ever lost.",
                        style = MaterialTheme.typography.bodyLarge, color = palette.text2,
                    )
                }
                OnboardingViewModel.RehearsalUi.Filing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.primary)
                        Spacer(Modifier.size(Spacing.s3))
                        Text("Filing your win…", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
                    }
                    Spacer(Modifier.height(Spacing.s3))
                    Text(
                        "Watch it drop into your framework — this is what happens every time you log.",
                        style = MaterialTheme.typography.bodyLarge, color = palette.text2,
                    )
                }
                OnboardingViewModel.RehearsalUi.Prompt -> {
                    Text("Try it — log your first win.", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
                    Spacer(Modifier.height(Spacing.s3))
                    Text(
                        "Something you did recently — big or small. Speak it, type it, or scan it, and watch BragBuddy file it into your framework.",
                        style = MaterialTheme.typography.bodyLarge, color = palette.text2,
                    )
                    Spacer(Modifier.height(Spacing.s4))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        CaptureHintChip(Icons.Outlined.Mic, "Speak", palette)
                        CaptureHintChip(Icons.Outlined.Keyboard, "Type", palette)
                        CaptureHintChip(Icons.Outlined.PhotoCamera, "Scan", palette)
                    }
                }
            }
        }

        DotProgress(index = 5)
        Spacer(Modifier.height(Spacing.s4))
        when (rehearsal) {
            is OnboardingViewModel.RehearsalUi.Ready, is OnboardingViewModel.RehearsalUi.Saved -> {
                PrimaryPill("Start logging", palette, onClick = onFinish)
                Spacer(Modifier.height(Spacing.s2))
                SecondaryText("Log another later", palette, onClick = onFinish)
            }
            OnboardingViewModel.RehearsalUi.Filing -> {
                SecondaryText("Skip for now", palette, onClick = onFinish)
            }
            OnboardingViewModel.RehearsalUi.Prompt -> {
                PrimaryPill("Log a win", palette, onClick = { CaptureLauncher.openChooser(context, null) })
                Spacer(Modifier.height(Spacing.s2))
                SecondaryText("Skip for now", palette, onClick = onFinish)
            }
        }
        Spacer(Modifier.height(Spacing.s4))
    }
}

/** The made-real "YOUR RECORD · READY" card — the WelcomeStep mock, now filled with the user's real
 *  first win. [filed] false = the honest degraded state (saved, not yet in a category). */
@Composable
private fun RealRecordCard(bullet: String, goalArea: String?, filed: Boolean, palette: BragPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Text(
            if (filed) "YOUR RECORD · READY" else "YOUR RECORD · SAVED",
            style = MaterialTheme.typography.labelSmall,
            color = palette.text3,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.s3))
        if (filed && goalArea != null) {
            MockPillarRow(PillarRamp[0].solid, goalArea, palette)
            Spacer(Modifier.height(Spacing.s2))
        }
        Row(verticalAlignment = Alignment.Top) {
            Text("•", style = MaterialTheme.typography.bodyLarge, color = palette.text2, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(Spacing.s2))
            Text(bullet, style = MaterialTheme.typography.bodyLarge, color = palette.text1)
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.positiveSoft)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = palette.positive, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                if (filed) "Filed & review-ready" else "Saved & safe",
                style = MaterialTheme.typography.labelSmall, color = palette.positive, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecoverStep(
    drive: OnboardingViewModel.DriveStepState,
    configured: Boolean,
    onConnect: () -> Unit,
    onRestore: () -> Unit,
    onSkip: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val connected = drive.connectedEmail != null
    val busy = drive.checking || drive.restoring
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(999.dp)).background(palette.primarySoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.CloudDownload, null, tint = palette.primary, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(Spacing.s4))
            Text("Recover your record", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
            Spacer(Modifier.height(Spacing.s3))
            Text(
                "Reinstalling? If you backed up to Google Drive before, restore everything — your entries, projects, framework and settings — in one tap. New here? Just skip.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.text2,
            )
            Spacer(Modifier.height(Spacing.s4))
            when {
                !configured -> RecoverInfoCard("Drive backup isn't set up on this build yet — you can skip this step.", palette)
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = palette.primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(Spacing.s3))
                    Text(
                        if (drive.restoring) "Restoring your record…" else "Checking your Drive…",
                        style = MaterialTheme.typography.bodyMedium, color = palette.text2,
                    )
                }
                connected && drive.backupExists -> FoundBackupCard(drive.connectedEmail!!, palette)
                connected -> RecoverInfoCard("No BragBuddy backup found for ${drive.connectedEmail}. You're all set to start fresh.", palette)
            }
            drive.message?.let {
                Spacer(Modifier.height(Spacing.s3))
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.extraInk)
            }
        }

        DotProgress(index = 2)
        Spacer(Modifier.height(Spacing.s4))
        when {
            busy -> Unit // no tappable action while a sign-in / restore is in flight
            !configured -> PrimaryPill("Continue", palette, onClick = onSkip)
            !connected -> {
                PrimaryPill("Connect Google Drive", palette, onClick = onConnect)
                Spacer(Modifier.height(Spacing.s2))
                SecondaryText("Skip for now", palette, onClick = onSkip)
            }
            drive.backupExists -> {
                PrimaryPill("Restore this backup", palette, onClick = onRestore)
                Spacer(Modifier.height(Spacing.s2))
                SecondaryText("Not now — keep it and start fresh", palette, onClick = onSkip)
            }
            else -> PrimaryPill("Continue", palette, onClick = onSkip)
        }
        Spacer(Modifier.height(Spacing.s4))
    }
}

@Composable
private fun FoundBackupCard(email: String, palette: BragPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.positiveSoft)
            .padding(Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(Radii.md)).background(palette.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CheckCircle, null, tint = palette.positive, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text("Backup found", style = MaterialTheme.typography.titleSmall, color = palette.positive, fontWeight = FontWeight.Bold)
            Text(email, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        }
    }
}

@Composable
private fun RecoverInfoCard(text: String, palette: BragPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).padding(Spacing.s3),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.text2)
    }
}

// ---------------- Shared pieces ----------------

@Composable
private fun MockPillarRow(dot: Color, label: String, palette: BragPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.size(Spacing.s2))
        Text(label, style = MaterialTheme.typography.titleSmall, color = palette.text2, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaptureHintChip(icon: ImageVector, label: String, palette: BragPalette) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.text2, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExampleChip(label: String, palette: BragPalette, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.text2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun DotProgress(index: Int) {
    val palette = BragBuddyTheme.palette
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(TOTAL_STEPS) { i ->
            val active = i == index
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (active) 22.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) palette.primary else palette.border),
            )
        }
    }
}

@Composable
private fun PrimaryPill(text: String, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryText(text: String, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = palette.text3, fontWeight = FontWeight.SemiBold)
    }
}
