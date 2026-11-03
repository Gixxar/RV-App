# The Poultons’ RV Logbook — Android

An Android logbook for trips and vehicle care, with records saved offline and an online travel map. Runs on Android 8.0 and later. Miles, litres and GBP. No account, ads or automatic location tracking. Internet permission loads the map background, supports explicit place searches and sends selected stop coordinates to the road-routing service when you tap Calculate.

## Install on your Android phone

Download the signed development APK supplied with the build. Open it on your phone and allow the downloading app to install unknown apps if Android asks. Open **Poultons RV Logbook**. Add your current mileage first, then your service reminders. Enable notifications using **Service reminders → Notification settings**.

A GitHub build also places the APK under **Actions → Android → the successful run → Artifacts → Poultons-RV-Logbook-Android**. Download and unzip that artifact to find `app-debug.apk`.

This is a development-signed personal-use build, not a Play Store release. A release channel needs a private, persistent signing key and release testing. Development APKs signed by different machines may require an uninstall to install another build; **export a backup first**, because uninstalling removes the logbook.

## RV dimensions, routing and campsite fit (version 1.7)

Open **My RV dimensions** on the overview, in Records settings, in Campsites or in the route planner. Save travelling **length, height and width in metres**, and **loaded weight in kilograms**. The previously stated 11 m length is an editable suggestion, not a verified measurement. Other measurements are blank until entered. Include roof equipment and travelling accessories; confirm mirror and towing requirements. Optional **Axle count** and **Maximum loaded axle (kg)** refine routing. The saved profile is a single logbook record, included in backups.

A new plan defaults to **Use my RV dimensions for this route** when a complete profile is saved. RV mode sends the chosen stop coordinates and measurements to [FOSSGIS Valhalla](https://github.com/valhalla/valhalla#demo-server) using its [truck costing model](https://valhalla.github.io/valhalla/api/route/api-reference/). All four dimensions are passed explicitly; kilograms are converted to metric tonnes. Optional axle details are passed too. If axle count is omitted the provider's default of five is shown, and axle-load checks are disabled unless a load is entered. Requests keep restriction/access checks enabled, use an identifying User-Agent/X-Client-Id and share the existing minimum 1.1-second request interval. This remains a personal-use public-service integration; no availability guarantee is provided.

**Truck routing is a planning aid, not certified RV navigation.** Mapped restrictions may be incomplete or wrong, and HGV access rules can be stricter than RV rules. Check road signs, clearances and campsite approaches. No route is accepted if the provider reports an adjusted setting, fails to confirm truck routing, or returns an invalid response. Network/no-route errors keep the draft and never silently substitute a car route. Unchecking the RV option explicitly selects the existing OSRM car route. Routes keep a snapshot of the measurements used; changing/removing the profile invalidates the planner preview and requires recalculation before saving. Previously saved plan records retain their original snapshots. Google Maps handoff warns that RV dimensions and the RV route are not transferred.

In **Campsites → Edit record**, enter the operator's maximum accepted RV length, height, width and weight, leaving unknown limits blank. The list compares these with your RV and can filter **Within recorded RV limits**, **Exceeds recorded RV limits** or **Needs confirmation**. Any exceeded known limit takes priority. Missing limits and values exactly at a stated limit require confirmation; they are never presented as a full fit. Even a full size match still needs confirmation of entrance, turning room, pitch and awning space. Google Maps imports do not supply reliable site limits, so imported sites begin unconfirmed.

Pitch availability is **manual, not a live booking feed**. Record Not checked, Enquiry sent, Spaces reported or Fully booked, with the checked date for the latter two. A fit result does not imply a vacant pitch; contact the campsite for current availability and confirm the relevant stay dates. All limits, reported availability and checked dates are preserved in backup/restore. Backups with an RV profile or RV-mode plan need version 1.7 or later; earlier backups still restore into 1.7. Application ID, SQLite schema and the retained development signing key remain unchanged.

## Route planner (version 1.6)

Open **Plan a route** on the overview or travel map, or **Records → Route plans**. Choose a start and destination, with up to eight intermediate stops (ten points total). Use address/postcode search, decimal coordinates, a point on the map, saved visited places, or a saved campsite name/address search. Confirm search results and campsite entrances. Reorder stops with Up/Down or remove them; the route follows the chosen order rather than optimizing it.

Tap **Calculate road route** to request a road-following route, distance in miles and estimated driving minutes from [OSRM’s public car-routing server](https://github.com/Project-OSRM/osrm-backend/wiki/Demo-server). It requires internet and sends only selected coordinates to that service; address searches use the Android geocoding provider. Routing requests have a minimum 1.1-second interval, timeouts, a 4 MB response limit and a 20,000-point geometry limit. Stops must be matched to a road within 500 metres; choose a closer road entrance if a stop cannot be routed. Public service availability is not guaranteed. See [provider usage/privacy information](https://routing.openstreetmap.de/about.html).

**These are car routes. RV height, width, weight, length, bridge clearances and access restrictions are not checked.** Driving estimates exclude traffic, breaks and time at stops. Check the route and restrictions for your RV before driving. There are no automatic toll/ferry avoidance controls, live traffic, route optimization or RV-specific routing in this version.

Name the plan, optionally add a travel date, link a saved trip and add notes, then **Save plan**. Plans persist offline and appear as dashed blue lines in a separate **Route plans** layer on the travel map. Saving a plan does not create a visit or a travelled-route record. Editing stop positions/order clears the old preview and requires recalculation before saving; changing the name/date/trip/notes retains the calculated route. Cancel requires confirmation after changes. Rotation retains the route in memory; process recreation keeps stop/text fields but may require recalculation for an unsaved route.

**Navigate a leg in Maps** opens the selected adjacent pair in Google Maps (or a browser). It may calculate a different route and uses its own routing rules; this is a handoff, not built-in turn-by-turn or RV-aware navigation. Saved plans can be reopened, edited and deleted. Backups include plans, stop order, geometry and estimates; backups containing plans require version 1.6 or later. Existing backups continue to restore into 1.6. The database schema and application ID stay the same, and the supplied installer retains the 1.3–1.5 signing key.

## Google Maps campsite lists (version 1.5)

Open **Campsites → Import Google Maps list**. This imports exported files; it does not sign in to Google, sync lists or scrape shared-list links.

1. Open [Google Takeout](https://takeout.google.com/), deselect all products and select **Saved**, then create a one-off ZIP export. [Google’s saved-list export instructions](https://support.google.com/maps/answer/7280933?co=GENIE.Platform%3DAndroid&hl=en) describe this route.
2. Download the ZIP to your phone. In the app, choose that ZIP or an extracted list CSV. For a ZIP containing several lists, select the campsite list you want.
3. Review the new places, untick any that are not campsites or that you do not want, then tap **Import**. Cancel makes no changes. The preview reports duplicate links and unsupported/incomplete rows.

The CSV needs **Title** and **URL** headings (case-insensitive); **Note**, **Comment**, **Tags** and **Address** are optional. UTF-8/BOM, quoted commas, escaped quotes and multiline notes are supported. Google Maps place links, including short links, are kept in Notes alongside the source list name. Imported records get an **Open in Google Maps** action; no link is fetched during import. Ordinary saved websites, missing names and unsupported URLs are skipped. Other saved places cannot be identified automatically as campsites: choose the right list and review the ticked places.

Duplicate detection compares retained Google Maps links within the list and against existing campsite Notes. The same link is skipped on repeat imports, including after renaming the campsite or restoring a backup. Different links for the same place may need manual cleanup; editing/removing the retained link changes this identity. Existing campsites and other logbook records are never overwritten. Imports save as one transaction, so a failed import adds nothing. Original list order is preserved among new campsites. Names, notes, optional addresses and source links use existing campsite fields and round-trip through backups.

Saved places are not evidence of a visit. Imports leave stay dates empty and do not create visited-place pins or routes. Use **Open in Google Maps** to view the original place; add confirmed visits separately on the travel map. Addresses are retained only when supplied in the CSV; the app does not guess addresses or coordinates from names or short links.

Limits: 2,000 nonblank data rows per CSV or ZIP, 10 MB input, 20 MB total expanded ZIP, 500 ZIP entries, 100 CSV lists, 64 columns and 16,000 characters per field. ZIP entries are read in memory and never extracted. Malformed CSV, invalid UTF-8, unsupported headers and exceeded limits stop the import before saving. For larger exports, choose a single extracted campsite CSV. The supplied 1.5 APK keeps the 1.3/1.4 development signing key, application ID and database format, so it can update those supplied versions in place.

## Professional interface (version 1.4)

The interface now uses a compact branded header, consistent medium-weight typography, white cards and a quieter cream/forest-green palette. Persistent Home, Map, Checks and Records navigation groups the logbook into clear sections. The overview highlights odometer, costs, service reminders and checklist progress. Record forms use labelled inputs, inline date controls and a fixed Cancel/Save bar; empty lists explain how to begin. The original RV banner and crest remain.

Version 1.4 keeps the application ID and database format, and the supplied APK uses the retained 1.3 signing key, allowing an in-place update from the supplied 1.3 build. Existing trips, checklist ticks, routes, reminders and other records are retained.

## Poster checklists (version 1.3)

The dashboard now has **Pre-trip inspection checklist** (24 items) and **Arrival / setup checklist** (12 items), transcribed from the supplied poster in its displayed order. Each has its own saved ticks, notes, add/edit/delete controls, reset confirmation and dashboard progress. Resetting one list does not reset the other.

The database upgrade replaces untouched, unchecked old starter entries with the poster items. Custom, renamed, checked or annotated checks are retained, and matching poster names are not duplicated. Reopening the app does not re-add checks you intentionally deleted. Restoring a pre-1.3 backup applies the same migration and adds arrival checks; current backups carry a template version so intentional deletions and ticks round-trip unchanged. Backups with the new arrival record type require version 1.3 or later.

**Installer transition:** the temporary signing key used for supplied versions 1.0–1.2 was not retained. The supplied 1.3 APK therefore uses a new development key. If an earlier version is installed, export its JSON backup and keep original photos before uninstalling it, install 1.3, then restore that backup and reattach photos. The new key is retained separately for subsequent development updates; it must never be committed to this repository. For those builds, set `RV_APP_SIGNING_KEY` to the private `RV-App-development-signing-key.jks` file (standard development alias/password: `androiddebugkey` / `android`). CI without that key produces independently signed test APKs.

## Travel map (version 1.2)

Open **Travel map · routes & visited places** on the dashboard.

- **Add place:** tap the map, confirm the coordinates, then save a name/date and optionally choose a saved trip. **Find place** searches a town/postcode/address through the phone’s Android geocoding provider; choose and confirm a result before saving. Direct decimal coordinate entry is also available under Places visited.
- **Import GPX:** choose a recorded GPX track or route file, name it, link it to a trip and save. Solid green paths display the imported coordinates. Track segment gaps stay separate. Import limit: 5 MB / 20,000 points / 500 segments; every segment needs at least two points. GPX track and GPX route sources remain distinguishable in the record.
- **Draw route:** tap waypoints, use Undo if needed, then Save route. Dashed gold lines are straight connections between manually chosen points; they are not road routing or verified GPS tracks. Manual drawing is limited to 500 points.
- Filter the map by the saved **Trip** label; **Fit all** frames the visible records. Layer controls toggle places, GPX paths and manual paths. Tap a pin/line to edit it; place popups can open the phone’s maps app.
- Existing trip titles, From/To text and campsite addresses are not automatically geocoded. Add places or import/draw paths to populate the map. Link records using Choose saved trip or a matching Trip label.
- The OpenStreetMap background requires internet; ordinary cached tiles may remain available, but this is not an offline-map download feature. Saved pins and paths stay in SQLite and are rendered locally. The tile provider receives requests for the viewed area; searches go through the Android geocoding provider. The app does not request GPS/location permission or record journeys automatically.
- Places and route geometry are included in JSON backup/restore. Older backups can be restored into 1.2; backups containing these new record types require 1.2 or later. Original photo files still need separate saving.

Map code bundles Leaflet 1.9.4 and its licence under `app/src/main/assets/map`. Only HTTPS OpenStreetMap tiles are fetched; bundled scripts/styles load locally. Map attribution stays visible, a distinct app User-Agent is sent, and normal WebView HTTP caching is retained. There is no bulk tile prefetch. The local map has no JavaScript-to-Java bridge and navigation/resource access is restricted. Android provider availability and live tile delivery still need phone acceptance testing.

## Adventure poster styling (version 1.1)

The interface follows the supplied Poulton’s RV poster: original RV banner and crest, warm paper backgrounds, dark ink/forest-green section bars, gold borders/stars, condensed headings and RV-style footer stripes. The dashboard uses touch-sized feature cards and adapts to one column on narrow screens or larger font settings. All list screens, record forms, checkboxes, buttons and system-bar colours use the same palette. The 1.1 styling update retained the 1.0 application ID, database schema and signing key. For the later signing-key transition, see the version 1.3 instructions above.

## Features

| Area | Available behavior |
| --- | --- |
| Travel map | Visited-place pins, GPX track/route imports, manual route drawing, trip filters, place search and external maps |
| Dashboard | Recorded odometer, total fuel/expense/maintenance costs, due service count, departure progress |
| Trip logs | Name, date picker, route, start/end odometer, notes; add, edit, delete |
| Pre-trip inspection | 24 poster checks, custom checks, notes, saved tick state, independent reset |
| Arrival / setup | 12 poster checks, custom checks, notes, saved tick state, independent reset |
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
- The app has no cloud sync, GPS recording, multiple vehicles, automatic recurring maintenance logs or live sensor connection. Photo associations are text labels. Database and file operations are synchronous for a small personal logbook.

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
3. Verify all 24 pre-trip and 12 arrival checks against the poster. Tick items in both lists; reset each independently. Edit notes, add a custom item and delete a default; verify those choices after relaunch and a backup/restore.
4. Add a service due today and one due at the current mileage. Enable notification permission and confirm the due alert. Complete a repeating service and verify its next date/mileage.
5. Add a photo, rotate while editing, save, relaunch and open it. Cancel a picker without losing your form.
6. Export a backup. Restore it, confirming replacement, and verify records. Reattach the photo. Try a malformed backup and verify the existing data remains.
7. Add/edit/delete fuel, expenses and campsite records. Cancel a deletion and confirm the entry remains.
8. Open the travel map, add a place, draw a manual route and import a GPX track. Link both to a saved trip; filter and toggle layers. Confirm segment gaps are not joined and manual connections are dashed. Tap a place and open it in Maps. Back up and restore these records. Test map tile loading and address search on your phone.
9. Check the layout and keyboard on your Samsung phone. Background alert delivery can be delayed by Android battery settings.

## Architecture

One application module, Java 17, native Android views and SQLite, plus a restricted WebView with bundled Leaflet 1.9.4 for the travel map. `Module` defines stable persisted keys; `Rules` validates forms/imports; `LogStore` owns SQLite; `Backup` validates and transfers records; `ServiceNotifications` manages Android jobs/channels; `MainActivity` owns the logbook screens; `TravelMapActivity`, `GeoData` and `GpxImport` handle maps and route geometry.

Database schema version 2 stores module-tagged JSON; its non-destructive upgrade installs the poster checklists. `ChecklistTemplates` owns the transcribed defaults and migration rules. Keep field keys stable; introduce explicit, non-destructive migrations before schema changes. Tests use JUnit 4 and Robolectric.
