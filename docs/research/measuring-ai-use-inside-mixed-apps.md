# Measuring AI use inside apps that also do other things: options and limits

Status: exploration. Official Android, Play and Telegram pages re-read on 2026-11-16. Nothing here was built or tested on a device. Statements about how a specific chat app behaves are unverified.

## The problem
Android's usage events report which app was in the foreground. The event reference lists events such as `ACTIVITY_RESUMED` with the package name. It does not list a conversation or website. So time in a browser or a chat app that can also host an AI assistant cannot be attributed from usage events alone. Source: https://developer.android.com/reference/android/app/usage/UsageEvents.Event

## Options considered
1. Separate apps and installed web apps. The web.dev page says that when Chrome installs a PWA as a WebAPK on a supported Android device, Chrome generates an APK for it (https://web.dev/articles/webapks). Whether that gives it its own usage record, and what package name it gets, was not checked here. Browser tabs stay mixed.
2. Manual tagging. A Quick Settings tile (https://developer.android.com/develop/ui/views/quicksettings-tiles) or an in-app button could let the user mark a time range as AI use. This depends on the user remembering and should be labelled self-reported.
3. Notification listener. An app that declares a notification listener service is called when notifications are posted or removed (https://developer.android.com/reference/android/service/notification/NotificationListenerService). That could count AI replies arriving from user-chosen chats. This gives activity bursts, not seconds of use, and muted or hidden notifications would be missed. Sideloaded apps can also hit Android's restricted-settings step (https://support.google.com/android/answer/12623953). Which fields a given chat app puts in its notifications was not checked.
4. Export and import. The user shares an exported chat file to the app (`ACTION_SEND`, https://developer.android.com/develop/ui/compose/sharing/send), which reads timestamps on the device. Whether each chat app offers such an export was not verified here.
5. Logs kept by the assistant itself. Telegram's bot FAQ says a bot receives messages from private chats with users, with group visibility depending on privacy mode, so a bot only sees its own conversations (https://core.telegram.org/bots/faq). This counts only that one assistant and needs work outside the phone app.

## Options with a high privacy cost
- Accessibility service: Play's policy page says only services designed to help people with disabilities may declare themselves accessibility tools (https://support.google.com/googleplay/android-developer/answer/10964491). It can also expose on-screen content, so it is a poor fit for an app that promises not to read content.
- Local VPN or DNS logging: Android shows a connection-request dialog before a VPN app becomes active (https://developer.android.com/develop/connectivity/vpn). It would also need network access, which conflicts with a "no network permission" promise. What it can see for encrypted traffic was not checked.

## Unknowns
Accuracy of any of these was not measured. Any version of this feature would need a plain statement of what it does and does not count, and a clear opt-in for anything beyond usage events.
