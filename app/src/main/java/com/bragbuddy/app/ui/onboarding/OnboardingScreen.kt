package com.bragbuddy.app.ui.onboarding

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
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
import com.bragbuddy.app.ui.framework.FrameworkScreen
import com.bragbuddy.app.ui.legal.PrivacyContent
import com.bragbuddy.app.ui.role.RoleInput
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.PillarRamp
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * First-run onboarding wizard: **Welcome → Privacy (hard gate) → Role → Framework**. Guided but
 * skippable — role and framework can be skipped (framework then keeps `Framework.DEFAULT`); privacy is
 * required. Adopts the Design System §2 tone (warm intro, dot progress, pill CTA, the amber "no company
 * name" reassurance) but replaces the design's AI voice-refine framework step with the real Type + Scan
 * editor ([FrameworkScreen]) per the 2026-07-07 reshape — no AI ever rewrites the framework.
 *
 * When [reacceptOnly] is true the flow collapses to just the privacy card (a material privacy-version
 * bump for an already-onboarded user): accept re-stamps the version and finishes.
 */
private const val TOTAL_STEPS = 4

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
    var step by rememberSaveable { mutableIntStateOf(0) }
    when (step) {
        0 -> WelcomeStep(onNext = { step = 1 })
        1 -> PrivacyStep(dotIndex = 1, onAccept = { viewModel.acceptPrivacy(); step = 2 })
        2 -> RoleStep(
            initialRole = initialRole,
            onContinue = { role -> viewModel.saveRole(role); step = 3 },
            onSkip = { viewModel.skipRole(); step = 3 },
        )
        else -> FrameworkStep(onFinish = { viewModel.finish() })
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
        // Shared, scrollable Core Privacy Principles body.
        PrivacyContent(Modifier.weight(1f))

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

        DotProgress(index = 2)
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
private fun FrameworkStep(onFinish: () -> Unit) {
    val palette = BragBuddyTheme.palette
    // Hide the finish bar while a full-screen category/project editor sheet is open, so it can't be
    // tapped through the sheet (which would finish onboarding and drop unsaved editor text). The sheet
    // then covers the whole step; the bar returns when the sheet closes.
    var editingFramework by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(palette.bg)) {
        // The real Type + Scan editor (handles its own status-bar inset, scroll, and header).
        Box(Modifier.weight(1f)) {
            FrameworkScreen(contentBottomPadding = Spacing.s6, reportEditing = { editingFramework = it })
        }
        // Pinned finish bar (hidden while editing).
        if (!editingFramework) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s3)) {
                    DotProgress(index = 3)
                    Spacer(Modifier.height(Spacing.s3))
                    PrimaryPill("Start logging", palette, onClick = onFinish)
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
