package com.bragbuddy.app.data.legal

/**
 * The single source of truth for BragBuddy's privacy & terms copy. Pure Kotlin (no Android deps) so
 * the in-app screen ([com.bragbuddy.app.ui.legal.PrivacyContent]) and the hosted `docs/privacy.md`
 * mirror the exact same words.
 *
 * Style follows the creator's "Core Privacy Principles" reference — rounded grey cards, a bold title
 * and plain body — but the CLAIMS are rewritten for what is actually true of BragBuddy (a local-only,
 * no-account app that sends text/images/audio to **Groq** for processing — by default through
 * BragBuddy's own stateless relay [Phase M1], or direct with a BYOK key). Nothing here asserts
 * app-level at-rest encryption (we don't ship it — [Principle] 4 phrases it honestly) or "our servers"
 * (there are none). See PROGRESS.md for the reshape rationale.
 *
 * **[VERSION]** is the acceptance stamp: bump it only for a *material* change so the app re-prompts
 * (`SettingsStore.acceptedPrivacyVersion`). Cosmetic wording fixes should NOT bump it.
 *
 * NOT legal advice — a lawyer should review this before any public/Play launch.
 */
object PrivacyPolicy {
    /** Bump ONLY on a material change → the app re-prompts for acceptance.
     *  v2 (Phase M1): disclose the managed relay — by default entries pass through BragBuddy's own
     *  stateless relay to Groq (stores nothing, logs no content); a BYOK key still goes direct.
     *  v3 (Phase M2): disclose the offline image-scan queue — when you scan while offline the
     *  downscaled image is briefly saved on-device (parity with the offline voice clip), then deleted
     *  as soon as it's read. Material (a new on-device retention path) → re-accept. */
    const val VERSION = 3
    const val LAST_UPDATED = "12 July 2026"
    const val CONTACT_EMAIL = "simpleapps108@gmail.com"
    const val GOVERNING_LAW = "India"
    /** Groq is the AI processor. Stable site link (kept generic so it can't rot). */
    const val GROQ_URL = "https://groq.com"

    const val TITLE = "Core Privacy Principles"

    /** One warm line under the title. */
    const val INTRO =
        "BragBuddy is a private, on-device work journal. Here's exactly how it treats your data — " +
            "in plain language, and true to how the app actually works."

    data class Principle(val title: String, val body: String)

    val principles: List<Principle> = listOf(
        Principle(
            "Local-first — no account, no servers",
            "BragBuddy lives on your phone. There's no sign-up, no BragBuddy account, and no BragBuddy " +
                "server keeping your entries. Your record sits in the app's own private storage. The " +
                "only copy that ever leaves your device is one you choose to make — an optional backup " +
                "to your own Google Drive, or a file you export yourself.",
        ),
        Principle(
            "AI runs on Groq — via our relay or your own key",
            "To transcribe, clean, categorise and summarise your notes, BragBuddy sends the necessary " +
                "text — and any image you scan — to Groq, a third-party AI provider. Voice notes are " +
                "transcribed by Groq's Whisper. It reaches Groq one of two ways: through BragBuddy's own " +
                "relay (the managed default), or — if you add your own Groq key under Settings → AI " +
                "engine — straight to Groq with your key. Our relay simply forwards each request to " +
                "Groq and passes the answer back, storing none of your notes, images or audio and " +
                "keeping no log of their contents. To prevent abuse it counts how many " +
                "requests an install makes (tied to a random ID, never to you), never what they " +
                "contain. Either way, this is the one place your content leaves your device, and Groq " +
                "processes it under its own terms. Only send what you're comfortable sharing with an AI " +
                "provider.",
        ),
        Principle(
            "Audio and images aren't kept",
            "A voice note is transcribed and then the recording is discarded — only the text remains. " +
                "A scanned image is read and then dropped; we don't store it. If you're offline, a voice " +
                "clip or a scanned image may wait briefly in the app's own private storage so your " +
                "capture isn't lost, and is deleted as soon as it's transcribed or read. It's never " +
                "backed up and never leaves your device except to be read.",
        ),
        Principle(
            "Encrypted in transit — and honest about at rest",
            "Everything BragBuddy sends to Groq (directly or via our relay) or Google Drive travels " +
                "over HTTPS/TLS. On your device, " +
                "your record lives in BragBuddy's private, OS-sandboxed storage, protected by Android's " +
                "device encryption. BragBuddy does not add its own separate password or app-level " +
                "encryption layer — so please keep a screen lock on your phone.",
        ),
        Principle(
            "No ads, no tracking, no selling",
            "BragBuddy shows no ads, runs no analytics or advertising trackers, and never sells or " +
                "shares your data. There's no one to sell it to — there's no account and no server.",
        ),
        Principle(
            "You're in control — delete anytime",
            "Delete any entry or project, reset your framework, or clear the app's data whenever you " +
                "like, and it's gone from your device. If you use Drive backup, you manage or delete " +
                "those files in your own Google Drive.",
        ),
        Principle(
            "The AI can be wrong — no warranty",
            "BragBuddy uses AI to clean and organise your notes, and AI can misread, misfile or leave " +
                "things out. It's a helpful assistant, not an official system of record for HR, legal or " +
                "performance decisions — always review its output yourself. The app is provided “as " +
                "is”, without warranties of any kind, and to the fullest extent permitted by law we " +
                "are not liable for any loss or damage arising from your use of the app or from the AI's " +
                "output.",
        ),
        Principle(
            "Who it's for",
            "BragBuddy is intended for working adults (18 or older). It isn't directed at children.",
        ),
        Principle(
            "Governing terms & contact",
            "These principles are governed by the laws of $GOVERNING_LAW. We may update them from time " +
                "to time; a material change will ask you to accept again. Questions or requests: " +
                "$CONTACT_EMAIL.",
        ),
    )

    // -------- Concise onboarding variant (Phase 3) --------
    // The first-run privacy gate shows a SHORTER, de-keyed summary so it's actually read; the full
    // [principles] above stay the authoritative version in Settings → Privacy. Acceptance still binds
    // the full terms (the onboarding footer points there). This is a summary, NOT a material change —
    // do NOT bump [VERSION] for it. Crucially it drops the BYOK **key-instruction** wording ("using the
    // Groq API key you add yourself") — telling a first-run user to add a key is premature — while
    // keeping the material Groq disclosure. BYOK itself is unchanged; only the onboarding verbiage is.

    /** One short line for the onboarding gate (the full policy lives in Settings → Privacy). */
    const val ONBOARDING_INTRO =
        "The short version of how BragBuddy treats your data. The full policy is always in " +
            "Settings → Privacy."

    /** Shown when the concise gate is used, under the Groq link — points to the authoritative copy. */
    const val ONBOARDING_FULL_POLICY_NOTE = "Read the full privacy policy anytime in Settings → Privacy."

    /** The condensed, de-keyed principle set for first-run onboarding. Fewer cards, plain language,
     *  and no key-setup instructions — while still surfacing every material disclosure (Groq, no
     *  audio/image retention, control, no-warranty). */
    val onboardingPrinciples: List<Principle> = listOf(
        Principle(
            "Local-first — no account",
            "BragBuddy lives on your phone. No sign-up and no BragBuddy servers holding your entries. " +
                "The only copy that ever leaves your device is a backup you choose to make.",
        ),
        Principle(
            "AI runs on Groq",
            "To clean, categorise and summarise your notes, BragBuddy sends the necessary text — and " +
                "any image you scan — to Groq, a third-party AI provider, by default through BragBuddy's " +
                "own relay, which stores nothing and keeps no log of your content. That's the one place " +
                "your content leaves your device, so only share what you're comfortable sending to an AI.",
        ),
        Principle(
            "Audio and images aren't kept",
            "A voice note is transcribed and the recording is discarded; a scanned image is read and " +
                "then dropped. Only the text stays. If you're offline, a clip or image waits briefly on " +
                "your device so nothing is lost, then is deleted once it's processed.",
        ),
        Principle(
            "No ads, no tracking, no selling",
            "No ads, no analytics, no trackers — and your data is never sold or shared.",
        ),
        Principle(
            "You're in control",
            "Delete any entry or project, reset your framework, or clear the app's data anytime — and " +
                "it's gone from your device.",
        ),
        Principle(
            "The AI can be wrong",
            "AI can misread or misfile, so always review its output. BragBuddy is a helpful assistant, " +
                "provided “as is” — not an official record for HR or legal decisions.",
        ),
    )

    /** The emphasised closing — deliberately the strongest statement on the page. */
    const val CLOSING_TITLE = "You decide what you write"
    const val CLOSING_BODY =
        "Your notes and scans are sent to an AI provider (Groq) and may be quoted back to you in your " +
            "summaries. You are solely responsible for what you disclose. We strongly recommend that you " +
            "do NOT enter your employer's or clients' names, or any confidential, proprietary or personal " +
            "information — describe your work in general terms."
}
