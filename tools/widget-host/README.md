# Disposable Android widget host

This test-only APK hosts actual installed widget providers via Android AppWidgetHost. It is not a Samsung launcher, a mock rendering, or proof of physical-device acceptance. It has no network permission. Never use it with private profiles.

Qualification screen: 1080×2340 pixels at420dpi, logical width about411dp;5×6 grid,96dp-high cells,24dp horizontal page margins, normal font scale. Host default widget padding is retained. The public workflow records exact installed APK hashes and screenshots.1×1 counters use a single cell;5×1 strips and expanded views can be captured separately. Private screenshots are never fixtures.

Binding is granted only in the disposable emulator with `appwidget grantbind`. Synthetic demo opt-in is seeded in the disposable profiles, never enabled by default in the shipped applications. No release signing or distribution is performed.
