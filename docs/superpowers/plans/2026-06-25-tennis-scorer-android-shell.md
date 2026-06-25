# Tennis Scorer — Android Shell Implementation Plan (Plan 2 of 2)

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax. This plan was authored and executed in the same session; the authoritative result is the working `:app` module on the `android-shell` branch.

**Goal:** Wrap the tested `core` scoring engine in a persistent, interactive notification plus a Compose setup screen, so a match is scored entirely from the notification shade.

**Architecture:** A foreground `Service` owns an ongoing `NotificationCompat` rendered from the engine's `Scoreboard`. `MatchRepository` is the single source of truth: it holds the match (format, names, first server, ordered points) as a `StateFlow`, recomputes the `Scoreboard` via `ScoringEngine`, and persists JSON to app storage on every change. A `BroadcastReceiver` handles the `+P1 / +P2 / Undo` notification actions. `MainActivity` (Compose) sets up a match and mirrors the live score.

**Tech Stack:** Kotlin 2.0.21, AGP 8.5.2, Gradle 8.8, Jetpack Compose (BOM 2024.09.03) + Material 3, kotlinx.serialization, `androidx.lifecycle`/`activity-compose`. `minSdk 26`, `compileSdk 34`, `targetSdk 34`, JDK 17. Foreground service type `specialUse` (a scorekeeper fits no standard FGS type; fine for a personal/sideloaded build).

---

## File Structure

- `settings.gradle.kts` — add `pluginManagement` + `dependencyResolutionManagement` repos (google/mavenCentral) and `include(":app")`.
- `core/build.gradle.kts` — drop its local `repositories {}` (now centralized in settings).
- `app/build.gradle.kts` — Android application module; depends on `project(":core")`.
- `app/src/main/AndroidManifest.xml` — permissions, `MainActivity`, `ScoringService` (specialUse), `ScoreActionReceiver`, `BootReceiver`.
- `app/src/main/res/values/strings.xml`, `res/values/themes.xml`, `res/drawable/ic_score.xml` (app + notification icon).
- `app/src/main/java/com/tennisscorer/match/MatchState.kt` — `@Serializable` match data + `FormatPreset` list.
- `app/src/main/java/com/tennisscorer/match/MatchRepository.kt` — process-wide singleton: `StateFlow<MatchSession?>`, `start/addPoint/undo/end`, JSON persistence.
- `app/src/main/java/com/tennisscorer/service/Notifications.kt` — channel + `buildNotification(scoreboard, names)`.
- `app/src/main/java/com/tennisscorer/service/ScoringService.kt` — foreground service, observes the repository, re-posts the notification.
- `app/src/main/java/com/tennisscorer/service/ScoreActionReceiver.kt` — receives action intents, mutates the repository.
- `app/src/main/java/com/tennisscorer/service/BootReceiver.kt` — restarts the service if a match was in progress.
- `app/src/main/java/com/tennisscorer/MainActivity.kt` — Compose setup + live mirror, `POST_NOTIFICATIONS` request.

## Action protocol (notification ↔ app)

One receiver, action string `com.tennisscorer.action.SCORE`, extra `cmd` ∈ `{P1, P2, UNDO}`. Each notification button is a `PendingIntent.getBroadcast` with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT` and a distinct request code. The receiver loads `MatchRepository`, applies the command (which persists + recomputes), and the service re-posts the notification from the new `Scoreboard`. No activity trampoline (Android 12+ safe), since scoring never opens the UI.

## Tasks

- [ ] **Task 1 — Gradle wiring.** Update `settings.gradle.kts` (repos + `include(":app")`), trim `core/build.gradle.kts` repos. Verify `./gradlew :core:test` still green on JDK 17.
- [ ] **Task 2 — Manifest + resources.** Permissions: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `RECEIVE_BOOT_COMPLETED`. Declare the activity (launcher), service (`foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBCATEGORY`), and both receivers. Add `ic_score` vector, a `NoActionBar` Material3 theme, strings.
- [ ] **Task 3 — MatchState + repository.** `@Serializable MatchState(formatKey, p1Name, p2Name, firstServer, points: List<Player>)`; `MatchRepository` exposes `StateFlow<Scoreboard?>` + names, `start(...)`, `addPoint(Player)`, `undo()`, `end()`, persisting JSON via `context.filesDir` on every mutation and reloading on init.
- [ ] **Task 4 — Notification + service + receivers.** `Notifications.buildNotification` maps a `Scoreboard` to title `"P1 vs P2"`, text `"<phase/score line>"`, three actions, server marker, `setOngoing(true)`, silent low channel. `ScoringService.startForeground` with the specialUse type; collect the repository flow and re-`notify` on changes; stop self + remove notification on match end/none. `ScoreActionReceiver` applies commands. `BootReceiver` restarts the service when a saved match exists.
- [ ] **Task 5 — Compose UI.** Setup screen (two name fields, format dropdown defaulting to Pro set · no-ad, first-server toggle, Start) and live mirror (scoreboard + the same +P1/+P2/Undo + End match). On Start: request `POST_NOTIFICATIONS` (API 33+), then `startForegroundService`.
- [ ] **Task 6 — Build.** Write `local.properties` with `sdk.dir`, run `./gradlew :app:assembleDebug` on JDK 17, iterate to BUILD SUCCESSFUL.
- [ ] **Task 7 — Emulator smoke test.** Boot the `Pixel_9_Pro` AVD, `adb install` the APK, launch, start a match; assert via `adb shell dumpsys notification` that the ongoing notification shows the score, then `am broadcast` a `+point` and an `undo` and assert the text changes accordingly.

## Verification

- `./gradlew :core:test` stays green (23 tests).
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, an APK under `app/build/outputs/apk/debug/`.
- Emulator: ongoing notification posts with the live score; a broadcast `+point` increments it; `undo` reverts it; ending the match clears the notification.
- On the user's real phone (manual): swipe-resistance, lock-screen actions, multi-hour persistence, OEM battery behavior.
