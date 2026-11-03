# The Poultons’ RV Logbook — Android

An offline, native Android logbook for trips and vehicle care. Runs on Android 8.0 and later. Miles, litres and GBP. No account, server, ads, tracking or Internet permission.

## Install on your Android phone

Download the signed development APK supplied with the build. Open it on your phone and allow the downloading app to install unknown apps if Android asks. Open **Poultons RV Logbook**. Add your current mileage first, then your service reminders. Enable notifications using **Service reminders → Notification settings**.

A GitHub build also places the APK under **Actions → Android → the successful run → Artifacts → Poultons-RV-Logbook-Android**. Download and unzip that artifact to find `app-debug.apk`.

This is a development-signed personal-use build, not a Play Store release. A release channel needs a private, persistent signing key and release testing. Development APKs signed by different machines may require an uninstall to install another build; **export a backup first**, because uninstalling removes the logbook.

## Features

| Area | Available behavior |
| --- | --- |
| Dashboard | Recorded odometer, total fuel/expense/maintenance costs, due service count, departure progress |
| Trip logs | Name, date picker, route, start/end odometer, notes; add, edit, delete |
| Departure checklist | Eleven editable starter checks, custom checks, persistent tick state, confirmed reset |
| Maintenance | Work, date, odometer, cost, provider and notes |
| Service reminders | Due by date or mileage, completion history, optional repeat months/miles, daily Android notifications |
| Fuel and expenses | Litres, odometer, station, cost, category, description |
| Campsites | Address, stay dates, pitch, contact, facilities, notes |
| Photos | System photo/document picker, persistent source access, captions and trip/campsite labels |
| Mileage | Dated readings; highest logged trip/fuel/maintenance/mileage reading used for reminders |
| Backup and restore | Portable JSON records, system file picker, full validation before confirmed transactional replacement |

A service is due when **either** its date or mileage threshold is reached. Marking a repeating service complete retains its completed entry and creates the next reminder from **today and the current logged odometer**. Repeat months and miles control the next reminder's thresholds; a threshold without a repeat interval is removed from that next reminder. Completed service flags remain checked to avoid accidentally generating duplicate repeat reminders. Completing a reminder does not write a maintenance record: add details and costs under Maintenance.

The departure checklist is a starting point. Adapt it to your vehicle and its manual.

## Data and reminders

- Records live in private SQLite storage. Relaunching and updating with the same signing key retain them. Uninstalling or clearing app storage removes them.
- **Export a backup regularly** using the dashboard's Backup & restore screen. JSON backups are readable text: choose a private destination.
- Backups include photo captions and notes, **not original photo files or transferable photo permissions**. Keep photos separately and reattach them after restore. Deleting/moving a source photo or revoking its access can make it unavailable.
- Restore validates format, version, module names, field types, dates and amounts before a database transaction replaces existing data. Export the current logbook first. Import limit: 10 MB and 10,000 records.
- Android schedules a persisted daily background check. Alerts are approximate, depend on notification permission/settings and battery restrictions, and resume after reboot. Force-stopping the app suspends background work until it is opened again. The app also refreshes alerts when opened and when records change.
- Mileage reminders use recorded readings; there is no automatic vehicle connection or background mileage tracking.
- The app has no cloud sync, GPS, maps, multiple vehicles, automatic recurring maintenance logs or live sensor connection. Photo associations are text labels. Database and file operations are synchronous for a small personal logbook.

## Build and test

Install JDK 17 and Android SDK platform 35 / build tools 35.0.0. Open this folder in Android Studio, or set `ANDROID_HOME` / a local `sdk.dir` in `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows: use `gradlew.bat`. Gradle 8.9's wrapper and checksum are included; AGP is pinned to 8.7.3. Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions performs the same unit tests, APK assembly and lint, then uploads the APK and reports. Its SDK setup explicitly requests `platform-tools`, avoiding the retired Android `tools` package that blocked the original scaffold.

Tests cover date/amount validation, due boundaries, SQLite persistence/edit/delete, backup round trips and rejected imports, all module navigation, draft recovery during activity recreation, checklist completion/reset, repeat-service completion and notification scheduling/clearing. Robolectric tests exercise Android API 28, with navigation also checked on API 35; physical-phone behavior still needs the smoke test below.

## Phone smoke test

1. Install and launch. Add a mileage reading, a trip and maintenance work. Relaunch and verify saved records.
2. Try an ending odometer lower than its start and a campsite departure before arrival; both must be rejected.
3. Tick departure checks and reset them. Add a custom check. Verify its state after relaunch.
4. Add a service due today and one due at the current mileage. Enable notification permission and confirm the due alert. Complete a repeating service and verify its next date/mileage.
5. Add a photo, rotate while editing, save, relaunch and open it. Cancel a picker without losing your form.
6. Export a backup. Restore it, confirming replacement, and verify records. Reattach the photo. Try a malformed backup and verify the existing data remains.
7. Add/edit/delete fuel, expenses and campsite records. Cancel a deletion and confirm the entry remains.
8. Check the layout and keyboard on your Samsung phone. Background alert delivery can be delayed by Android battery settings.

## Architecture

One application module, Java 17, native Android views, no runtime third-party libraries. `Module` defines stable persisted keys; `Rules` validates forms/imports; `LogStore` owns SQLite; `Backup` validates and transfers records; `ServiceNotifications` manages Android jobs/channels; `MainActivity` owns the screens.

Database schema version 1 stores module-tagged JSON. Keep field keys stable; introduce explicit, non-destructive migrations before schema changes. Tests use JUnit 4 and Robolectric.
