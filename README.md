# RV-App

A small, offline Android RV logbook. One Java Android application module, native Android views, and SQLite; no account, network permissions, backend, or runtime third-party dependencies.

## Run

- Open this folder in Android Studio with JDK 17 and Android SDK 35 installed.
- Use Gradle 8.9 (Android Studio: Gradle settings → local Gradle distribution). Android Gradle Plugin is pinned to 8.7.3.
- This starter does not include the binary Gradle wrapper. With Gradle 8.9 installed, optionally generate it with `gradle wrapper --gradle-version 8.9`.
- Run `gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` or run the `app` configuration on an Android 8.0/API 26+ emulator or device.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- GitHub Actions installs the pinned build tooling, runs these same checks and uploads the debug APK.

## Included

| Area | First version |
| --- | --- |
| Trip logs | Name, date, route, starting/ending mileage, notes |
| Departure checks | Five editable starter checks, custom checks, tick off and reset |
| Fuel | Date, odometer, litres, GBP cost, station |
| Expenses | Date, category, GBP cost, description |
| Maintenance | Work, date, odometer, cost, provider, notes |
| Campsites | Address, stay dates, pitch, contact, facilities, notes |
| Photos | System document picker, persistent photo access, caption and trip/campsite label, open photo |
| Mileage | Dated readings; highest recorded odometer shown on Home |
| Service reminders | Due date and/or mileage, due indicator, completion toggle |

All records can be added, edited and deleted. Fields validate dates (`YYYY-MM-DD`), nonnegative finite numbers and trip mileage order. Each record's first field is required. Draft forms survive Android activity recreation. Miles, litres and GBP are fixed initial units.

## Structure

- `Module.java`: feature field definitions, with stable storage keys.
- `LogStore.java`: SQLite repository; records are module-tagged JSON payloads, schema version 1.
- `Rules.java`: date, numeric and reminder rules, with JVM unit tests.
- `MainActivity.java`: Home, lists and forms. Feature areas are logical modules in one Gradle module to keep this first build small.

For larger features, move forms into separate screens and migrate JSON records into typed tables using explicit, non-destructive database migrations. Do not rename persisted field keys without migration.

## Scope and limitations

Service reminders are evaluated when Home or the reminder list is opened; there are no scheduled/background notifications. Completing a reminder does not create its next occurrence. Photo records retain access to the original document rather than copying it, so deleting/moving that original or revoking provider access can make it unavailable. Trip/campsite photo links are text labels in this version. No cloud sync, export, maps, currency conversion or multiple-vehicle support yet. Records are private app data; uninstalling clears them, and backups are disabled. Database operations are synchronous for this small starter; move them off the main thread before supporting large datasets.

The departure checklist is an editable starting point; adapt it to the vehicle manual.

## Manual smoke test

1. Launch on API 26+; Home shows all nine areas and the starter departure checks.
2. Add and edit a trip. Reject an ending odometer lower than its start. Relaunch and verify persistence.
3. Tick a departure check, return to it, then reset all checks.
4. Add fuel, expense, maintenance, campsite and mileage records. Try invalid dates and negative costs.
5. Set a reminder due today, another at the recorded mileage and one in the future; confirm only the first two show due. Complete one and confirm the Home due count decreases.
6. Attach a photo, rotate/recreate the activity, save, relaunch and open it. Cancel the picker without losing form contents.
7. Edit and delete records; cancel deletion to confirm the record remains.

## Verification

The initial authoring environment did not contain an Android SDK, Gradle or Java compiler. APK compilation, lint, JVM tests and device smoke tests must be confirmed by CI/Android Studio; they are not claimed as passing based solely on source inspection.
