# BragBuddy — Core Privacy Principles

*Last updated: 12 July 2026 · Version 3*

BragBuddy is a private, on-device work journal. Here's exactly how it treats your data — in plain
language, and true to how the app actually works.

> This document mirrors the **Privacy & terms** screen inside the app (`Settings → Privacy & terms`),
> which every user accepts once on first run. It is the same wording, kept in sync with the app's
> `PrivacyPolicy` source of truth.

---

### Local-first — no account, no servers

BragBuddy lives on your phone. There's no sign-up, no BragBuddy account, and no BragBuddy server
keeping your entries. Your record sits in the app's own private storage. The only copy that ever leaves
your device is one you choose to make — an optional backup to your own Google Drive, or a file you
export yourself.

### AI runs on Groq — via our relay or your own key

To transcribe, clean, categorise and summarise your notes, BragBuddy sends the necessary text — and any
image you scan — to [Groq](https://groq.com), a third-party AI provider. Voice notes are transcribed by
Groq's Whisper. It reaches Groq one of two ways: through BragBuddy's own relay (the managed default),
or — if you add your own Groq key under **Settings → AI engine** — straight to Groq with your key. Our
relay simply forwards each request to Groq and passes the answer back, storing none of your notes,
images or audio and keeping no log of their contents. To prevent abuse it counts how many requests an
install makes (tied to a random ID, never to you), never what they contain. Either way, this is the one
place your content leaves your device, and Groq processes it under its own terms. Only send what you're
comfortable sharing with an AI provider.

### Audio and images aren't kept

A voice note is transcribed and then the recording is discarded — only the text remains. A scanned image
is read and then dropped; we don't store it. If you're offline, a voice clip or a scanned image may wait
briefly in the app's own private storage so your capture isn't lost, and is deleted as soon as it's
transcribed or read. It's never backed up and never leaves your device except to be read.

### Encrypted in transit — and honest about at rest

Everything BragBuddy sends to Groq (directly or via our relay) or Google Drive travels over HTTPS/TLS.
On your device, your record lives in BragBuddy's private, OS-sandboxed storage, protected by Android's
device encryption. BragBuddy does not add its own separate password or app-level encryption layer — so
please keep a screen lock on your phone.

### No ads, no tracking, no selling

BragBuddy shows no ads, runs no analytics or advertising trackers, and never sells or shares your data.
There's no one to sell it to — there's no account and no server.

### You're in control — delete anytime

Delete any entry or project, reset your framework, or clear the app's data whenever you like, and it's
gone from your device. If you use Drive backup, you manage or delete those files in your own Google
Drive.

### The AI can be wrong — no warranty

BragBuddy uses AI to clean and organise your notes, and AI can misread, misfile or leave things out.
It's a helpful assistant, not an official system of record for HR, legal or performance decisions —
always review its output yourself. The app is provided "as is", without warranties of any kind, and to
the fullest extent permitted by law we are not liable for any loss or damage arising from your use of
the app or from the AI's output.

### Who it's for

BragBuddy is intended for working adults (18 or older). It isn't directed at children.

### Governing terms & contact

These principles are governed by the laws of India. We may update them from time to time; a material
change will ask you to accept again. Questions or requests: **simpleapps108@gmail.com**.

---

## You decide what you write

**Your notes and scans are sent to an AI provider (Groq) and may be quoted back to you in your
summaries. You are solely responsible for what you disclose. We strongly recommend that you do NOT enter
your employer's or clients' names, or any confidential, proprietary or personal information — describe
your work in general terms.**
