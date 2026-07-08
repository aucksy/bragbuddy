package com.bragbuddy.app.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragbuddy.app.data.legal.PrivacyPolicy
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * The scrollable "Core Privacy Principles" body — shared by the onboarding hard gate and the
 * Settings → Privacy screen so the words never drift. Rounded grey cards, bold title + plain body
 * (the creator's reference style), with the emphasised closing rendered as a highlighted card.
 *
 * Content comes verbatim from [PrivacyPolicy]. [modifier] is where the host places it — the
 * onboarding step passes `Modifier.weight(1f)` so the Accept bar stays pinned below; Settings passes
 * `Modifier.fillMaxSize()`.
 */
@Composable
fun PrivacyContent(modifier: Modifier = Modifier) {
    val palette = BragBuddyTheme.palette
    val uriHandler = LocalUriHandler.current
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s4))
        Text(PrivacyPolicy.TITLE, style = MaterialTheme.typography.headlineLarge, color = palette.text1)
        Spacer(Modifier.height(Spacing.s2))
        Text(PrivacyPolicy.INTRO, style = MaterialTheme.typography.bodyMedium, color = palette.text2)
        Spacer(Modifier.height(Spacing.s2))
        Text(
            "Last updated ${PrivacyPolicy.LAST_UPDATED} · v${PrivacyPolicy.VERSION}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.text3,
        )
        Spacer(Modifier.height(Spacing.s4))

        PrivacyPolicy.principles.forEach { principle ->
            PrincipleCard(principle.title, principle.body, palette)
            Spacer(Modifier.height(Spacing.s3))
        }

        // Emphasised closing — the strongest statement on the page.
        Spacer(Modifier.height(Spacing.s2))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(palette.primarySoft)
                .border(1.5.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(Radii.lg))
                .padding(Spacing.card),
        ) {
            Text(
                PrivacyPolicy.CLOSING_TITLE,
                style = MaterialTheme.typography.titleMedium,
                color = palette.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(Spacing.s2))
            Text(PrivacyPolicy.CLOSING_BODY, style = MaterialTheme.typography.bodyMedium, color = palette.text1)
        }

        Spacer(Modifier.height(Spacing.s4))
        Text(
            "AI processing is provided by Groq — groq.com",
            style = MaterialTheme.typography.bodySmall,
            color = palette.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(Radii.sm))
                .clickable { uriHandler.openUri(PrivacyPolicy.GROQ_URL) }
                .padding(vertical = Spacing.s2),
        )
        Spacer(Modifier.height(Spacing.s6))
    }
}

@Composable
private fun PrincipleCard(title: String, body: String, palette: BragPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface2)
            .padding(Spacing.card),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text1, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s2))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.text2)
    }
}
