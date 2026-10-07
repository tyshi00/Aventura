# Changelog

## 1.3.0
- Fixed haptics not working at all: `serverPackage` in `lighttool.toml` was still pointing at the Android Studio emulator host instead of `com.lightos`, so the tool could never connect to LightOS to read the user's haptics preference ("Unable to bind to server").
- Added Backup & restore in Settings. "Back up now" saves your history, level, streak, and settings inside the app, verifies the saved file, and restores can be made from it with a confirm step showing how many entries it contains. Restores are all or nothing, and invalid files are rejected without touching your data. There is no clipboard backup: the on-device keyboard cannot paste, and nothing outside the phone can easily fill its clipboard, so it was never usable.
- Added a daily automatic backup. When you open Aventura, it saves a backup in the app if a day has passed since the last one, kept separate from your manual backup, with a "Restore from auto-backup" option. It never runs when there is no data, so an accidental reset can't cause it to overwrite your last good copy with an empty one, and a failed save is retried the next time you open the app.
- Settings and Backup & restore now use the same row layout as LightOS's own settings screens (row spacing, left edge, and toggle icon placement), matched against the LightPods reference.
- Backups now survive an uninstall. Each manual backup and the daily automatic one also saves a dated file to `Documents/Aventura`, keeping the newest few of each. After a reinstall Android won't let the app open its old files there, so copy one into the app's own folder with adb (the exact commands are shown on the Backup screen) and tap Restore from folder. Aventura creates that folder when it launches, and the home screen shows a hint when it looks like Aventura was installed there before. Behavior verified on a Light Phone III with a separate storage test app.
- Backups are now checked against sane limits before they can be restored: XP per entry (at most 500, where a real entry earns at most 95), number of entries (at most 15,000, about ten years of finishing everything), text lengths, dates, and file size. The XP total and level lookup can also no longer overflow or throw on bad data. Before this, a corrupted or hand-edited backup could pass the shape check and then crash the app on every launch.
- Added three more levels, so the top is now level 15, "Offline Success", at 28,000 XP (level 13 "Rooted" at 18,500 and level 14 "Unhurried" at 23,000). Levels 1 to 12 are unchanged, so nobody's current level moves. Players who had reached the old top level, "Reconnected", can now keep climbing.
- The Progress screen now lists only your newest 30 history entries, with a note that older ones still count. Nothing is deleted, because your level, streak, and trophies are worked out from the full history.
- Haptics now also fire on the top bar and bottom bar buttons and on text fields (the SDK's own components were still using the old tap handler).

## 1.2.0
- Added haptic feedback on tap, matching LightOS: every tappable surface now uses the SDK's new `lightClickable`, which fires a short vibration on finger-down when the user has haptics enabled in LightOS
- Settings menu items (toggles, reset) now use the larger Heading text size, matching LightOS's own settings screens
- Fixed inconsistent spacing: top bar padding, list item padding, and the quest checklist's row spacing now all follow the same rhythm used across LightOS
- History entries on the Progress screen now show at normal reading size instead of the smaller secondary-text size
- Removed the old Tracker reference app (`tool/`), it was never part of the Aventura build
- Rewrote both READMEs with proper installation instructions, licensing, and a disclaimer, and added the Light SDK's own README as `README.light-sdk.md`

## 1.1.3
- Moved the settings icon to the leftmost slot of the bottom bar, matching LightOS's own layout convention

## 1.1.2
- Moved the settings toggle icons to the left of their labels, matching how LightOS's own settings screens lay out toggles on the actual hardware

## 1.1.1
- Fixed the completed-quest checkmark: Light's ACCEPT icon was rendering as a solid triangle instead of a hollow tick (missing `fillType="evenOdd"` in the SDK's own asset), swapped it for a small custom checkmark drawn just for this

## 1.1.0
- Streaks and trophies are now optional, toggle them off in Settings if you just want the quests
- Fixed the quest checkbox: swapped the clipped SELECT_ON/SELECT_OFF icons for a CIRCLE + checkmark combo, and centered it against the quest text instead of top-aligning it
- New app icon: globe with faint continents and a compass rose
- Package renamed to `com.tyshi00.aventura`
- 34 new MONTHLY-only quests added to widen that tier's rotation pool
- READMEs rewritten to describe Aventura specifically, with screenshots and credit to Soto
- Added a GitHub Actions release workflow to build and attach the APK automatically

## 1.0.0
- Initial port of Soto's quest system to the Light Phone III, 3 daily / 4 weekly / 12 monthly quests, XP, levels, streaks, and trophies
