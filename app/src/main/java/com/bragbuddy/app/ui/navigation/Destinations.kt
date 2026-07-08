package com.bragbuddy.app.ui.navigation

import android.net.Uri

/** App navigation routes. Kept as simple string constants for the small graph. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"

    /** The Phase C first-run wizard (privacy hard gate → role → framework). Start destination when
     *  onboarding is incomplete, or when the accepted privacy version is stale. */
    const val ONBOARDING = "onboarding"

    /** The Phase C Privacy & terms screen, reached read-only from Settings. */
    const val PRIVACY = "privacy"

    /** The Phase 7 "Reliable reminders" guided screen (OEM battery/alarm wizard). */
    const val RELIABILITY = "reliability"

    /** Deep pillar view. The pillar id rides in the path; an optional `folder` query arg scopes the
     *  screen to a single folder (Home's "See more" target). */
    const val PILLAR = "pillar/{pillarId}?folder={folder}"

    /** Build a concrete route to a pillar's deep view (all folders). */
    fun pillar(pillarId: String): String = "pillar/$pillarId"

    /** Build a route scoped to a single folder within a pillar (Home "See more"). */
    fun folder(pillarId: String, folderName: String): String =
        "pillar/$pillarId?folder=${Uri.encode(folderName)}"
}
