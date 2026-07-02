package com.bragbuddy.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.R
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * Home (Design System §1). Phase 1 shows the captured entries newest-first; the empty state nudges
 * a first capture. The structured pillar document arrives in Phase 3.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_eyebrow),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.text1,
                )
            }
            // NOTE: Settings entry point isn't in the design (which shows "Summarise" here — a
            // Phase 5 feature). Temporary gear until the summary surface + a proper settings home land.
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings), tint = palette.text2)
            }
        }
        Spacer(Modifier.height(Spacing.s3))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState() }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = contentBottomPadding + Spacing.s4),
            ) {
                items(entries, key = { it.id }) { EntryCard(it) }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: EntryEntity) {
    val palette = BragBuddyTheme.palette
    val relTime = DateUtils.getRelativeTimeSpanString(
        entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val sourceLabel = if (entry.source == EntrySource.VOICE) "Voice" else "Typed"

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.primary),
        )
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.rawTranscript,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text1,
            )
            Spacer(Modifier.height(Spacing.s2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.source == EntrySource.VOICE) Icons.Outlined.Mic else Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = palette.text3,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    text = "$relTime · $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val palette = BragBuddyTheme.palette
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s6),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Inbox, null, tint = palette.primary, modifier = Modifier.size(30.dp))
        }
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = palette.text1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text3,
            textAlign = TextAlign.Center,
        )
    }
}
