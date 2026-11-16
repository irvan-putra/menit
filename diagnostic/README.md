# Menit Diagnostic

A synthetic-only test app for [Menit](https://github.com/irvan-putra/menit). It checks whether a header-only
accessibility reader and the interval logic behave as designed. It never reads real chats or other apps.

- Shows fake "Synthetic chat A/B" screens with fixed filler text.
- Optional accessibility service, limited by its config and its code to this app's own package. It compares a
  screen header with three fake names and stores only a typed label and a time.
- Interval logic (`Reconciler`) is tested on the host with a brute-force oracle.
- Permissions: usage access (optional comparison). No network permission, backups off.
- Local data can be archived (kept on the device) or permanently deleted from archive copies in a separate step.

This is a diagnostic, not a product. Estimates are not exact attention time.

License: GPL-3.0-only.

## Status (source review candidate; runtime unverified)
- Synthetic-only. No real chat or header capture. Detection on real apps is unverified.
- Archive keeps old logs; permanent deletion is a separate confirmed button.
- Time base is elapsed-realtime; a trailing-edge throttle reads the last event of a quick A to B switch.
- Signing: no signed APK or release is published. The manual signing workflow is only a draft reference in `workflows/sign-diag-apk.yml`; it is outside GitHub's active workflow directory. A dedicated key and main-restricted signing environment must be set up before any signing workflow is activated. No key or password is in this tree.
- Host checks: JUnit logic tests plus real-store scenarios. The repository-root diagnostic workflow runs both the host scenarios and Gradle tests/build without artifact upload. Not run on a device or emulator.
