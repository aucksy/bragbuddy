package com.bragbuddy.app.ui.capture

import android.content.Context
import android.content.Intent
import com.bragbuddy.app.data.prefs.CaptureMode

/**
 * The single place that launches the capture surface — introduced in Phase B to kill the five
 * copy-pasted `Intent(context, CaptureActivity::class.java)…startActivity` call sites (Home, the
 * pillar view, the shell FAB, the catch-up sheet, the notification).
 *
 * Each launch carries a [CaptureActivity.EXTRA_START_MODE] telling the surface what to open to:
 *  - an explicit [CaptureMode] name — the Home radial's pick (Speak / Type / Scan),
 *  - [CaptureActivity.START_ASK] — the 3-choice chooser (the in-context "+" rows), or
 *  - [CaptureActivity.START_DEFAULT] — the user's *Default capture method* (notification / nudges),
 *    resolved against settings inside the ViewModel.
 * A redo carries no start mode and opens in the last-used mode.
 */
object CaptureLauncher {

    private fun base(context: Context, project: String?, replaceId: Long?): Intent =
        Intent(context, CaptureActivity::class.java).apply {
            if (project != null) putExtra(CaptureActivity.EXTRA_PROJECT, project)
            if (replaceId != null && replaceId > 0L) putExtra(CaptureActivity.EXTRA_REPLACE_ID, replaceId)
        }

    /** Explicit mode (from the Home "+" radial pick), optionally anchored to a folder. */
    fun intentForMode(context: Context, mode: CaptureMode, project: String? = null): Intent =
        base(context, project, null).putExtra(CaptureActivity.EXTRA_START_MODE, mode.name)

    /** The 3-choice chooser (the in-context "+" rows), anchored to the tapped folder if given. */
    fun intentForChooser(context: Context, project: String? = null): Intent =
        base(context, project, null).putExtra(CaptureActivity.EXTRA_START_MODE, CaptureActivity.START_ASK)

    /** The user's *Default capture method* — resolved in the VM (notification / daily nudge / catch-up). */
    fun intentForDefault(context: Context, project: String? = null): Intent =
        base(context, project, null).putExtra(CaptureActivity.EXTRA_START_MODE, CaptureActivity.START_DEFAULT)

    /** Redo an existing entry (no start mode → opens in the last-used mode). */
    fun intentForRedo(context: Context, entryId: Long): Intent = base(context, null, entryId)

    fun openMode(context: Context, mode: CaptureMode, project: String? = null) =
        context.startActivity(intentForMode(context, mode, project))

    fun openChooser(context: Context, project: String? = null) =
        context.startActivity(intentForChooser(context, project))

    fun openDefault(context: Context, project: String? = null) =
        context.startActivity(intentForDefault(context, project))

    fun redo(context: Context, entryId: Long) =
        context.startActivity(intentForRedo(context, entryId))
}
