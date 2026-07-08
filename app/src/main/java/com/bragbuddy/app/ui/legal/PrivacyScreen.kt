package com.bragbuddy.app.ui.legal

import androidx.compose.foundation.layout.fillMaxSize
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
import com.bragbuddy.app.ui.theme.BragBuddyTheme

/**
 * Privacy & terms, reached from Settings (read-only — acceptance happens once in onboarding). The body
 * is the shared [PrivacyContent], so it's the exact same words the user accepted.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = { Text("Privacy & terms", style = MaterialTheme.typography.headlineMedium, color = palette.text1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = palette.text2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.text1),
            )
        },
    ) { inner ->
        PrivacyContent(Modifier.fillMaxSize().padding(inner))
    }
}
