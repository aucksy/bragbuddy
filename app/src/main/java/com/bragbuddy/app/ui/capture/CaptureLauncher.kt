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
 *  - an explicit [CaptureMode] name — the Home radial's pick (Speak / Type / Scan), or
 *  - [CaptureActivity.START_ASK] — the 3-choice chooser (the in-context "+" rows).
 * A redo carries no start mode and opens in the last-used mode. (The daily reminder no longer routes
 * here — it opens the Home "+" radial via [com.bragbuddy.app.MainActivity.EXTRA_OPEN_CAPTURE]; the
 * "default capture method" concept was removed in v0.29.1.)
 */
object CaptureLauncher {

    private fun base(context: Context, project: String?, deliverable: String?, replaceId: Long?): Intent =
        Intent(context, CaptureActivity::class.java).apply {
            if (project != null) putExtra(CaptureActivity.EXTRA_PROJECT, project)
            // A deliverable anchor is meaningless without its project (it lives inside one), so it only
            // rides when a project does — the tap-in sites always pass both.
            if (project != null && deliverable != null) putExtra(CaptureActivity.EXTRA_DELIVERABLE, deliverable)
            if (replaceId != null && replaceId > 0L) putExtra(CaptureActivity.EXTRA_REPLACE_ID, replaceId)
        }

    /** Explicit mode (from the Home "+" radial pick), optionally anchored to a folder / deliverable. */
    fun intentForMode(
        context: Context,
        mode: CaptureMode,
        project: String? = null,
        deliverable: String? = null,
    ): Intent = base(context, project, deliverable, null)
        .putExtra(CaptureActivity.EXTRA_START_MODE, mode.name)

    /** The 3-choice chooser (the in-context "+" rows), anchored to the tapped folder / deliverable. */
    fun intentForChooser(context: Context, project: String? = null, deliverable: String? = null): Intent =
        base(context, project, deliverable, null).putExtra(CaptureActivity.EXTRA_START_MODE, CaptureActivity.START_ASK)

    /** Redo an existing entry (no start mode → opens in the last-used mode). */
    fun intentForRedo(context: Context, entryId: Long): Intent = base(context, null, null, entryId)

    fun openMode(context: Context, mode: CaptureMode, project: String? = null, deliverable: String? = null) =
        context.startActivity(intentForMode(context, mode, project, deliverable))

    /** Log into a deliverable — the **tap-in** path (owner's call, 2026-07-17): this pins the entry to
     *  [project] + [deliverable] with NO AI guess at either. The deterministic route is deliberately the
     *  primary one, because a third level makes the model's guess strictly harder and mis-classification
     *  is the complaint this phase exists to answer. */
    fun openChooser(context: Context, project: String? = null, deliverable: String? = null) =
        context.startActivity(intentForChooser(context, project, deliverable))

    fun redo(context: Context, entryId: Long) =
        context.startActivity(intentForRedo(context, entryId))
}
