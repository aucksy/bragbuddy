package com.bragbuddy.app.ui.reliability

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.notification.Notifications
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * "Reliable reminders" — the Phase 7 OEM battery/alarm wizard (PRD P0-1: reminders must survive
 * ColorOS-style battery management). A stepped checklist with live ✓ status where the OS lets us
 * read it, system dialogs/screens where it doesn't, a user-confirmed auto-start step (no public
 * API), and an honest "send a test reminder". Not in the design files — built from tokens; flagged.
 *
 * Every step launches through an activity-result launcher so returning from the system screen
 * re-probes the state (and re-arms the alarm) immediately.
 */
@Composable
fun ReliabilityScreen(
    onBack: () -> Unit,
    viewModel: ReliabilityViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val health by viewModel.health.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Any system screen we bounce through refreshes the probes on return.
    val systemScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    fun launch(intent: Intent, fallback: Intent? = null) {
        runCatching { systemScreen.launch(intent) }.onFailure {
            fallback?.let { fb -> runCatching { systemScreen.launch(fb) } }
        }
    }

    fun appDetailsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    fun openNotificationSettings() = launch(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        fallback = appDetailsIntent(),
    )

    // When only the "Daily reminder" CHANNEL is blocked (long-press → turn off), the fix lives on
    // the channel page, not the app page.
    fun openReminderChannelSettings() = launch(
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_REMINDER),
        fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            launch(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                fallback = appDetailsIntent(),
            )
        }
    }

    fun openBatteryExemption() = launch(
        // The direct system "Allow?" dialog (needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — fine for
        // a direct-APK app; Play-restricted, like USE_EXACT_ALARM, if that ever changes).
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun openAutostartSettings() {
        // Best-effort deep links into the OEM auto-start managers (no public API, screens move
        // between OS versions) — first one that resolves wins; app details is the safe fallback.
        val candidates = listOf(
            // ColorOS (OPPO / Realme / recent OnePlus — the creator's Find X9s lives here)
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // MIUI / HyperOS
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Vivo / Funtouch
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Huawei / EMUI
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component)
            if (runCatching { systemScreen.launch(intent); true }.getOrDefault(false)) return
        }
        launch(appDetailsIntent())
    }

    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text("Reliable reminders", style = MaterialTheme.typography.headlineMedium, color = palette.text1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = palette.text2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.text1),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Text(
                "Phones like this one pause background apps to save battery — which can silently " +
                    "swallow the daily nudge. These quick switches keep it alive.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text3,
            )
            Spacer(Modifier.height(Spacing.s4))

            StepCard(
                palette = palette,
                done = health.notificationsEnabled,
                title = "Allow notifications",
                body = if (health.reminderChannelBlocked) {
                    "The “Daily reminder” notification itself is turned off (a long-press can do that) — switch it back on."
                } else {
                    "The reminder is a notification — it can't appear if they're blocked."
                },
                actionLabel = if (health.reminderChannelBlocked) "Turn the reminder back on" else "Open notification settings",
                onAction = {
                    if (health.reminderChannelBlocked) openReminderChannelSettings() else openNotificationSettings()
                },
            )
            Spacer(Modifier.height(Spacing.s3))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                StepCard(
                    palette = palette,
                    done = health.exactAlarmsAllowed,
                    title = "Allow exact alarms",
                    body = "Lets the reminder fire at your chosen minute instead of whenever the system wakes up.",
                    actionLabel = "Allow exact alarms",
                    onAction = ::openExactAlarmSettings,
                )
                Spacer(Modifier.height(Spacing.s3))
            }

            StepCard(
                palette = palette,
                done = health.batteryUnrestricted,
                title = "Don't optimize battery for BragBuddy",
                body = "Battery optimization can freeze the app in the background, so the alarm never reaches it.",
                actionLabel = "Allow in background",
                onAction = ::openBatteryExemption,
            )
            Spacer(Modifier.height(Spacing.s3))

            // Auto-start can't be verified programmatically — the user confirms it by hand.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(palette.surface)
                    .padding(Spacing.card),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(palette, done = if (settings.oemAutostartDone) true else null)
                    Spacer(Modifier.width(Spacing.s3))
                    Text(
                        "Allow auto-start",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.text1,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    "On ColorOS and similar phones, look for “Auto-launch” or “Allow auto-start” and turn " +
                        "it on for BragBuddy. The phone doesn't tell apps whether it's set — tick it below " +
                        "once you have.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                PillAction("Open phone settings", palette, ::openAutostartSettings)
                Spacer(Modifier.height(Spacing.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "I've allowed auto-start",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text2,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.oemAutostartDone,
                        onCheckedChange = { viewModel.setAutostartDone(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.primary,
                            uncheckedTrackColor = palette.surface2,
                            uncheckedBorderColor = palette.border,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.s4))

            // The honest end-to-end check: post the real reminder right now.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(palette.surface)
                    .padding(Spacing.card),
            ) {
                Text("Try it", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "Sends the daily reminder notification right now, so you can see it works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                PillAction("Send a test reminder", palette) { viewModel.sendTestReminder() }
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    "It should pop up right away — if nothing appears, fix the steps above and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }

            Spacer(Modifier.height(Spacing.s6))
        }
    }
}

/** One verifiable step: live status dot + explanation + the system screen that fixes it. */
@Composable
private fun StepCard(
    palette: BragPalette,
    done: Boolean,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(palette, done)
            Spacer(Modifier.width(Spacing.s3))
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.text1, modifier = Modifier.weight(1f))
            if (done) {
                Text(
                    "Done",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.positive,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s2))
        Text(body, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        if (!done) {
            Spacer(Modifier.height(Spacing.s3))
            PillAction(actionLabel, palette, onAction)
        }
    }
}

/** ✓ (done) / attention (needs action) / neutral ring (unknown — the manual auto-start step). */
@Composable
private fun StatusDot(palette: BragPalette, done: Boolean?) {
    val fill = when (done) {
        true -> palette.positiveSoft
        false -> palette.extraSoft
        null -> palette.surface2
    }
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(fill)
            .border(1.dp, palette.border, RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (done) {
            true -> Icon(Icons.Filled.Check, null, tint = palette.positive, modifier = Modifier.size(14.dp))
            false -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(palette.extra))
            null -> Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(palette.text3.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun PillAction(label: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.primarySoft)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}
