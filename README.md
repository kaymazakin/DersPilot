# DersPilot

DersPilot is a native Android study planner app built with Java and the Android SDK.

The app is designed for students and self-learners who want a simple way to organize courses, learning goals, topics, daily plans, focus sessions, and quiz practice.

## Features

- Study goal and course management
- Topic tracking with priority, difficulty, status, notes, and estimated study time
- Automatic daily, weekly, and goal-based study planning
- Pomodoro focus timer
- Manual quiz creation and quiz results
- Progress tracking
- Light, dark, and system theme modes
- Local JSON-based data storage
- Notification support
- Prepared structure for future Pro and ad monetization features

## Tech Stack

- Java
- Android SDK
- Native Android UI
- SharedPreferences with JSON storage
- Android Notifications
- Custom APK build script

## Build

This project uses a lightweight Android SDK build script instead of Gradle.

```powershell
node build-apk.js
```

The generated APK is ignored by Git and will be created as:

```text
DersPilot.apk
```

## Notes

The current repository does not include real AdMob or Google Play Billing SDK integration. The UI and product flow are prepared for those features, but production release requires adding official SDKs, store product IDs, privacy policy, data safety declarations, and release signing.
