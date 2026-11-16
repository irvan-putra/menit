# Menit

An Android app that estimates how much of your phone time goes to AI.

## Privacy

- The only permission is Usage access (PACKAGE_USAGE_STATS), granted by you in system settings.
- No network permission, no upload, export or sharing feature, no content provider or service, and backups are turned off. The app stores nothing on the phone.
- This is not a guarantee against everything: Android itself, screenshots you take, or a modified build could do things this code does not. Check the source.
- No screen contents, notifications or messages are read.

## What it measures

The time each app was the one on screen, from Android's UsageStats events. Screen off, shutdown or another app resuming ends a session. Apps in a built-in list (ChatGPT, Gemini, Claude, Copilot, Perplexity, Grok, DeepSeek, Character.AI, Poe, Mistral) count as AI. Package IDs were checked against their Play Store pages on 2026-11-16.

## Limits

- It is an app-based estimate. Time in an AI app is not always AI use: account pages, settings and built-in browsing in those apps count too.
- Browsers and chat apps (Chrome, WhatsApp, Telegram, the Google app...) can host AI, but Android does not say what you did inside them. Their time is shown separately as "May include AI" and is never added to the AI total.
- Android's event records can have gaps, and how long they are kept depends on the device. Split screen and pop-up windows count only the last app touched. Foreground time is not attention. AI running with the screen off (voice mode, background generation) is not counted. AI features inside other apps (keyboards, photo editors, system assistants) are not detected.

## Roadmap (not built)

- Automatic updates, so test builds do not need manual installs.
- User-editable AI app list.
- Telling AI websites or bots apart inside mixed apps. This remains unbuilt; see [exploratory measurement options and limits](docs/research/measuring-ai-use-inside-mixed-apps.md). Candidates need a clear opt-in, minimal data (never raw screen content) and a check against Android's restricted settings for sideloaded apps.
- A stable signing key per app, so updates install over earlier builds.

## Build and CI

`gradle :app:testDebugUnitTest :app:assembleDebug` (Gradle 8.7, JDK 17, Android SDK 34), or `tools/build-local.sh SDK_DIR VERSION` for a Gradle-free build. CI runs tests and builds a debug APK on pushes to `main` and pull requests. Those runs do not upload an APK. A manual workflow dispatch can upload a versioned debug APK artifact only when `build_test_artifact` is explicitly enabled. This is a test artifact, not a signed release; there is no release publication or stable signing key.

The app has no third-party runtime libraries. JUnit (EPL-1.0) and Robolectric (MIT) are test-only dependencies; the Android Gradle Plugin (Apache-2.0) is a build tool. They are not bundled in the APK.

## License

SPDX-License-Identifier: GPL-3.0-only

The Menit source is licensed under GPL-3.0-only. See [LICENSE](LICENSE) for the full license text.
