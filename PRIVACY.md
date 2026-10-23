# Privacy

Stretch is an offline app. This page describes exactly what it does with your data,
and what it is technically incapable of doing.

## Short version

- **No network access.** The app does not declare `android.permission.INTERNET`, so
  Android will refuse any attempt to open a socket. It cannot phone home, fetch ads,
  check for updates, or upload anything — not by policy, but by the permission model.
- **No accounts, no analytics, no crash reporting, no advertising SDKs.** The shipped
  APK contains no third-party tracking code (see the audit below).
- **No location, camera, microphone, contacts, or storage permissions.**
- Everything the app knows lives in its own private storage and never leaves the phone.

## What is stored

Six values in the app's private `SharedPreferences` file (`stretch.xml`), readable only
by this app:

| value | example | why |
|---|---|---|
| wake time | 07:00 | when to send the morning nudge |
| bedtime | 22:30 | when to send the evening nudge |
| morning reminder on/off | true | your switch |
| evening reminder on/off | true | your switch |
| rotation cursor | 5 | which stretch the next session starts from |
| sessions completed | 12 | the count on the home screen |

There is no history, no timestamp of when you actually stretched, and no record of what
you did — only a counter that goes up.

`android:allowBackup="false"` is set, so these values are **not** included in Android's
cloud backup or device-to-device transfer.

## Permissions

| permission | why it is needed | when it is asked |
|---|---|---|
| `POST_NOTIFICATIONS` | to show the reminder | once, on first launch (Android 13+) |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | the nudge must fire at the time you set, not "sometime this hour" | declared; Android may require you to allow it in Settings |
| `RECEIVE_BOOT_COMPLETED` | Android forgets all alarms across a reboot, so the app re-arms them once at boot | never prompted |
| `VIBRATE` | the phone ticks when a stretch block changes | never prompted |
| `WAKE_LOCK` | the alarm fires while the device is dozing | never prompted |

There are no other permissions in the shipped manifest.

## Audit performed on the release APK

Run against `app-release.apk` (v1.2):

```bash
# 1. permissions actually in the APK
aapt2 dump permissions app-release.apk
#   -> 6 permissions, no INTERNET, no location, no storage, no camera

# 2. third-party SDKs in the compiled code
unzip -o app-release.apk 'classes*.dex' && strings classes*.dex \
  | grep -iE 'firebase|crashlytics|appsflyer|adjust|amplitude|mixpanel|segment\.io|gms/analytics|sentry'
#   -> no matches attributable to an analytics SDK. Substring hits exist
#      (Compose's "Adjust" icon class, "SegmentedButton", okio's SegmentedByteString)
#      and were checked by hand.

# 3. components another app could reach
#   -> MainActivity exported (launcher, required)
#   -> ReminderReceiver, BootReceiver: exported=false
#   -> androidx.profileinstaller.ProfileInstallReceiver removed from the manifest
#   -> androidx.startup.InitializationProvider: exported=false
```

Also checked:

- **No hard-coded secrets.** The release signing key and its password live outside the
  repository (`keystore/`, gitignored); a fresh clone cannot build a signed release.
- **No WebView, no JavaScript, no HTML rendering.** The UI is native Compose.
- **No logging of personal data.** The app writes nothing to logcat in release builds.
- **All artwork is local.** `assets/stretches.json` is pose data; `assets/ref/*.webp`
  are AI-generated illustrations. Nothing is fetched or attributed to a third party.
- **Backup and transfer disabled**, as above.

## What the app cannot do

Because there is no `INTERNET` permission:

- it cannot send your times, usage, or device identifiers anywhere;
- it cannot load remote images, fonts, or configuration;
- it cannot be silently updated outside the Play Store or a manual APK install.

The only outbound interactions are **intents to Android's own settings screens**, and
only in response to a tap (opening this app's notification settings, or the exact-alarm
settings page).

## Imported video

If you import your own footage, the app stores a `content://` URI for each clip in the
same private preferences file, plus a persisted read grant from Android's document
picker. That means:

- the app can read **only the files you picked**, and only for as long as you leave the
  grant in place;
- your video is **never copied** into the app's storage, and never uploaded — there is
  still no network permission;
- removing a clip in the app, or deleting the original file, ends the app's access.

Nothing about a clip is analysed: no frame extraction, no pose detection, no metadata
reading beyond the display name used to match it to a stretch.

## Notifications

Reminders are posted by the app itself through `AlarmManager`, with content generated
on the device. They appear on your lock screen like any other notification; that is a
normal Android notification, not a message sent from a server. You can switch each
reminder off in the app, or revoke notification permission in Android settings at any
time, and the app will simply stop nudging.

## Removing your data

Uninstalling the app deletes the `SharedPreferences` file with everything in it. There
is no server-side copy to delete, because there is no server.
