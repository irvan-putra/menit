# Android notes for a companion app that reads another app's usage data

Status: Android developer pages read on 2026-11-16. Claims are what the page says. Anything not on a page is labelled unverified. The pages read are named per item.

## Letting other apps signed by a known certificate call yours
- `protectionLevel="signature|knownSigner"` plus `knownCerts` (API 31 and up): "the system grants that permission to a requesting app if any signer in the requesting app's signing lineage, including the current signer, matches one of the digests that's declared with the permission in the `knownCerts` attribute." https://developer.android.com/guide/topics/permissions/defining . The manifest reference adds that if the requester's certificate is listed, the permission is granted "without notifying the user": https://developer.android.com/guide/topics/manifest/permission-element
- Scope: this grants the permission to any app signed with a listed certificate, not to one named app. It identifies a signer, not a package. The trust scope is every app signed by allowed certificates, so assess signer trust accordingly.
- Unverified: behaviour below API 31 (the flag does not exist there), and what happens if the requesting app was installed before the permission was declared.
- A caller's UID or package name alone is not proof of identity. The sender-of-pending-intents page recommends a Service or ContentProvider, where `Binder.getCallingUid()` gives the caller's UID and `PackageManager.getPackagesForUid()` maps it to packages. https://developer.android.com/privacy-and-security/risks/sender-of-pending-intents . That page does not say how to verify the caller's certificate; a design built on it would need its own certificate check (for example `PackageManager.hasSigningCertificate`, not read here) and has to handle UIDs shared by several packages and apps being reinstalled or updated.

## What "foreground time" can honestly mean
- On Android 10 and higher all visible activities stay `RESUMED` in multi-window mode; an activity can still be paused if a transparent activity covers it or it is not focusable (for example picture-in-picture), and it is possible that no activity has focus. On Android 9 and lower only the last-interacted activity is `RESUMED`. https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
- So `RESUMED` alone does not show that an app is visible or focused, and neither shows attention or reading. Any usage metric should be named only for what was actually measured; whether a given signal measures "focused time" needs its own test.
- `KeyguardManager.isKeyguardLocked()` and `isDeviceLocked()` exist, for excluding time while locked. https://developer.android.com/reference/android/app/KeyguardManager
- Unverified: whether touches inside a WebView reach `onUserInteraction()`, so an interaction-based idle cutoff is untested.

## Keeping a local data store out of backups
- For apps targeting API 31 or higher, on devices from some manufacturers `allowBackup="false"` disables cloud backup but does not disable device-to-device transfer. Auto Backup has separate cloud-backup and device-transfer sections that are configured through data extraction rules. https://developer.android.com/identity/data/autobackup
- The page's rule syntax lists the domains root, file, database, sharedpref, external and the device-protected ones device_root, device_file, device_database and device_sharedpref. A rule set that only names the first group does not exclude device-protected storage. Whether any given app writes there was not checked.
