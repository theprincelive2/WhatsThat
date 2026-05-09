# WhatsThat

WhatsThat is a personal Android notification history app for saving WhatsApp notifications on the device after the phone owner grants Notification Access.

## What It Does

- Listens for WhatsApp and WhatsApp Business notifications using Android NotificationListenerService
- Saves sender, message text, package name, and timestamp locally
- Groups saved notices into a WhatsApp-style inbox and conversation view
- Hides common system-generated notification noise with Clean view
- Lets the user block, unblock, delete, export, and mark saved notices
- Saves viewed WhatsApp statuses to local photo and video folders after folder approval

## Privacy Note

This app is intended only for the phone owner's own device with clear permission. It cannot read old WhatsApp chats, muted chats that do not create notifications, deleted messages, or messages that never appeared in notifications.

## Build On GitHub

1. Push the code to GitHub.
2. Open the repository.
3. Go to **Actions**.
4. Run **Build Android**.
5. Download one of the workflow artifacts:
   - `WhatsThat-debug-apk` for testing.
   - `WhatsThat-release-apk` for signed release APKs when signing secrets are configured.
   - `WhatsThat-play-store-aab` for Play Store upload when signing secrets are configured.

## Local Build

```bash
./gradlew assembleDebug
```

On Windows, use:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
