# WhatsThat

WhatsThat is a personal Android notification history app for saving WhatsApp notifications on the device after the phone owner grants Notification Access.

## What it does

- Listens for WhatsApp notifications using Android NotificationListenerService
- Saves sender, message text, package name, and timestamp locally
- Shows captured messages in the app
- Allows the user to clear saved history

## Privacy note

This app is intended only for the phone owner’s own device with clear permission. It cannot read old WhatsApp chats, muted chats that do not create notifications, or messages that never appeared in notifications.

## Build APK on GitHub

1. Push the code to GitHub.
2. Open the repository.
3. Go to **Actions**.
4. Run **Build Android APK**.
5. Download the APK from the workflow artifacts.

## Local build

```bash
gradle assembleDebug
```

The debug APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
