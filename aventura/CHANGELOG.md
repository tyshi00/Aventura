# Changelog

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
