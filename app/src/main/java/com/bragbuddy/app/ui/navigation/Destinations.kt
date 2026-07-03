package com.bragbuddy.app.ui.navigation

/** App navigation routes. Kept as simple string constants for the small graph. */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"

    /** Deep pillar view. The pillar id (a url-safe slug) rides in the path. */
    const val PILLAR = "pillar/{pillarId}"

    /** Build a concrete route to a pillar's deep view. */
    fun pillar(pillarId: String): String = "pillar/$pillarId"
}
