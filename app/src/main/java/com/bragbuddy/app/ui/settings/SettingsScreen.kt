package com.bragbuddy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.bragbuddy.app.BuildConfig
import com.bragbuddy.app.R
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.Spacing

/** Settings — Phase 0 placeholder. Real sections (framework editor, backup, reminder) land later. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.text1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.nav_home),
                            tint = palette.text2,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bg,
                    titleContentColor = palette.text1,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .padding(inner)
                .padding(horizontal = Spacing.screen),
        ) {
            InfoRow(label = "AI engine", value = viewModel.aiProviderLabel, palette = palette)
            InfoRow(
                label = "Version",
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                palette = palette,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, palette: com.bragbuddy.app.ui.theme.BragPalette) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s3)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        Text(value, style = MaterialTheme.typography.titleMedium, color = palette.text1)
    }
}
