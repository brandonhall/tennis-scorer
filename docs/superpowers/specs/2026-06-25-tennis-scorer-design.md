# Tennis Scorer — Design Spec

- Date: 2026-06-25
- Status: Approved design, pre-implementation
- Platform: Android only

## 1. Purpose & context

A personal Android app to keep score of a junior tennis match from the
notification shade. The parent scores with quick taps (one per point) without
losing access to the rest of the phone. Live scoring lives in a persistent,
interactive notification; match setup lives in the app. One match at a time. No
accounts, no cloud, no saved history — live scoring only.

The proof that this is buildable: media-player notifications. Play/pause/skip
live in the notification and on the lock screen and control playback without
opening the app. This app uses the same mechanism for `+point` / `undo`.

## 2. Goals / non-goals

**Goals**

- Persistent, swipe-proof, interactive notification with `+point` (per player)
  and `undo` controls that update the score in place.
- Correct tennis scoring for a configurable format, defaulting to the user's
  kid's format (see §3).
- Serve tracking with automatic rotation and a server indicator.
- Robustness: never lose an in-progress match (survive OS process kill, crash,
  or reboot).
- Minimal courtside friction: two names and a Start button for the common case.

**Non-goals (YAGNI)**

- No match history or saved results.
- No stats (aces, double faults, winners, errors).
- No multiplayer, sync, cloud, or accounts.
- No iOS build. (If ever wanted, iOS would be a separate Swift/ActivityKit
  Live Activity, not a port of this — different mechanism and constraints.)
- No tournament/bracket/draw management.

## 3. Scoring model (the engine)

**Event-sourced.** A match is an ordered list of point events, each
`PointWonBy(player)`. The displayed scoreboard is a *pure function* of
`(events, format, firstServer)`. Undo = drop the last event and recompute. There
is no mutable score state to keep in sync.

Payoffs: unlimited and always-correct undo; the logic has zero Android
dependencies, so it can be unit-tested exhaustively (tennis scoring has nasty
edge cases).

### 3.1 Format config

```
MatchFormat(
  setsToWin: Int,          // 1 = a single set decides the match (pro set)
  gamesToWinSet: Int,      // 8 for a pro set; 6 for a standard set
  winByTwoGames: Boolean,  // 2-game margin to win a set? false for the pro-set default
  tiebreakAtGames: Int?,   // 7 -> tiebreak at 7-7; null -> advantage set (no tiebreak)
  tiebreakPoints: Int,     // 7
  noAd: Boolean,           // true = no-ad (deciding point at deuce)
  decidingSet: DecidingSet // FULL_SET | MATCH_TIEBREAK(points) — multi-set formats only
)
```

**Default preset = the user's format ("Pro set · no-ad"):**
`setsToWin = 1`, `gamesToWinSet = 8`, `winByTwoGames = false`,
`tiebreakAtGames = 7`, `tiebreakPoints = 7`, `noAd = true`.
Games are first-to-8 with no two-game-margin requirement; the only win-by-two is
the tiebreak. Because the tiebreak triggers at 7–7, the set ends 8–6 (or better)
outright, or 8–7 through the tiebreak.

**Shipped presets** (selectable in setup; default is first):

1. **Pro set · no-ad** (default) — first to 8 games (no two-game margin),
   7-point tiebreak (win by two) at 7–7, no-ad games.
2. **Best of 3, full sets** — sets to 6 by two, 7-point tiebreak at 6–6, ad,
   full third set.
3. **Best of 3, 10-pt 3rd** — as above, but the deciding set is a 10-point
   match tiebreak.
4. **Best of 3, no-ad, 10-pt 3rd** — no-ad games, 10-point match-tiebreak third.
5. **Single set to 6** — one set, 7-point tiebreak at 6–6, ad.
6. **Custom** — every lever adjustable (sets, games per set, ad/no-ad, tiebreak
   on/off and length, deciding-set type).

### 3.2 Point scoring

- Display points: 0 / 15 / 30 / 40.
- **Ad scoring:** 40–40 is deuce; win a point → advantage; win again → game;
  lose the advantage point → back to deuce.
- **No-ad scoring:** first to 4 points wins the game; at 3–3 the next point is a
  deciding point that wins the game (no advantage).
- **Tiebreak:** numeric points; first to `tiebreakPoints`, win by two; entered
  when games reach `tiebreakAtGames` each; the tiebreak winner is credited the
  deciding game (e.g. set recorded 8–7).
- **Match tiebreak** (deciding-set option for multi-set formats): first to N
  (e.g. 10), win by two.

### 3.3 Games & sets

- Win a game → increment that player's games in the current set.
- Win the set at `gamesToWinSet`, requiring a two-game margin only when
  `winByTwoGames` is set (standard sets), or by winning the tiebreak at the tie
  threshold. The pro-set default has no game-margin rule; a 7–7 tie goes straight
  to the tiebreak.
- Win the match at `setsToWin` sets. For the user's default (`setsToWin = 1`),
  the single pro set ends the match.

### 3.4 Serve

- First server chosen at setup.
- Serve alternates every game.
- **Tiebreak rotation:** the player due to serve serves point 1, then the two
  players alternate serving two points each; after the tiebreak, serve passes
  correctly to the next game's server (relevant for multi-set formats).
- Server is derivable purely from event count and position; no separate stored
  state. The scoreboard exposes the current server for display.

### 3.5 Scoreboard output (what the UI renders)

```
Scoreboard(
  players: (name, name),
  completedSets: List<(Int, Int)>,        // games per player for finished sets
  currentGames: (Int, Int),
  currentPoints: (String, String),        // "0","15","30","40","Ad", or TB number
  phase: PLAY | DEUCE | TIEBREAK | MATCH_TIEBREAK | MATCH_OVER,
  server: Player,
  winner: Player?                         // set when phase == MATCH_OVER
)
```

## 4. Android architecture

Native Kotlin; Jetpack Compose for the single screen.

1. **`core` (pure Kotlin, no Android deps):** `ScoringEngine`, `MatchFormat`,
   `Scoreboard`, `Point`, presets. Fully unit-tested. Kept as an isolated
   package so it can later be extracted to its own Gradle module.
2. **`MatchRepository`:** single source of truth for the active match (format,
   names, first server, event list, start time). Persists to disk on every
   mutation. Exposes the current `Scoreboard` (e.g. as a `StateFlow`).
3. **`ScoringService` (foreground `Service`):** owns the ongoing notification;
   renders `Scoreboard` into a `NotificationCompat` and re-posts on each change;
   keeps the process alive for the duration of the match.
4. **`ScoreActionReceiver` (`BroadcastReceiver`):** receives the `+P1` / `+P2` /
   `Undo` `PendingIntent`s, applies them via `MatchRepository`, triggers a
   notification re-render.
5. **`MainActivity` (Compose):** setup screen, live mirror, end/new-match;
   requests `POST_NOTIFICATIONS` (API 33+).
6. **`BootReceiver`:** if a match was in progress, restart `ScoringService` to
   restore the notification after a reboot.

### 4.1 The notification

- Standard `NotificationCompat` template for the MVP (no custom `RemoteViews`):
  three actions fit cleanly and the standard template is the most robust across
  OEM skins.
- **Collapsed (quick glance):** title `"P1 vs P2"`, text
  `"Pro set · {games} · {points}"` with a server dot; three action buttons.
- **Expanded:** two-row scoreboard (games + points per player) with the server
  dot; same three action buttons.
- `setOngoing(true)`; silent, low-importance dedicated channel (no sound/vibrate
  per point).
- Actions `"+ P1"`, `"+ P2"`, `"Undo"` via `PendingIntent`s to
  `ScoreActionReceiver`. Long names are truncated; buttons may fall back to
  first name / initials when space is tight.

### 4.2 Data flow for one tap

```
button tap
  -> PendingIntent
  -> ScoreActionReceiver
  -> MatchRepository.addPoint(player) | undo()
  -> persist to disk
  -> recompute Scoreboard (ScoringEngine)
  -> ScoringService re-renders the notification
  (MainActivity, if foregrounded, observes the same repository StateFlow)
```

## 5. Persistence & robustness

- Active match serialized to JSON (kotlinx.serialization) in app-private
  storage, written synchronously on every point, undo, or setup change. Payload
  is tiny (format + names + first server + event list + start time).
- On service/app start: load the in-progress match if one exists; otherwise idle.
- On match end: show the final score, then clear the active match (no history is
  kept).
- Reboot: `BootReceiver` restarts the service when an in-progress match exists.
- **Foreground service type** (API 34+): declare `specialUse` with a short
  justification. Acceptable for a personal/sideloaded app; would need a review
  justification only if ever published to Play.
- **Notification permission** (API 33+): requested on first launch / first match
  start. If denied, the app explains the notification is the core surface and
  offers to open system settings.

## 6. Tech & build

- Kotlin, Jetpack Compose, AndroidX, `NotificationCompat`, kotlinx.serialization.
- `minSdk 26` (notification channels), `targetSdk` current.
- Build with JDK 17 (AGP 8+). The machine's `PATH` `java` is 11; use Android
  Studio's bundled JDK 17, or install 17 and point Gradle at it. (First-build
  setup note, not a code concern.)
- Android SDK already present at `~/Library/Android/sdk`.
- Single `app` module to start, with a clearly separated `core` package for the
  pure scoring logic.

## 7. Testing

- **Engine (TDD, exhaustive):**
  - Standard game progression 0 → 15 → 30 → 40 → game.
  - Ad deuce/advantage battles, including lost-advantage back to deuce.
  - No-ad deciding point at 3–3.
  - Set-win logic for both win-by-two formats (standard set ending 7–5) and
    no-margin formats (the pro set to 8).
  - Reaching 7–7 → tiebreak; tiebreak to 7 win-by-two including 7–6 (continue)
    and 8–6 (win); set credited 8–7.
  - Tiebreak serve rotation (1 then alternating 2s).
  - Full pro-set match end: 8–6 outright and 8–7 via tiebreak.
  - Undo returns to the exact prior scoreboard from every state, including
    across game/set/tiebreak boundaries; undo at match start is a no-op.
  - Multi-set presets (best of 3, 10-point match-tiebreak decider) for
    regression, even though the default is the pro set.
- **Repository:** persistence round-trip (save → load yields the same
  scoreboard).
- **Service / notification:** lighter. Instrumented test for the action-receiver
  → repository wiring; manual device/emulator verification of the tap loop,
  swipe-resistance, surviving an app swipe-away, and reboot restore.

## 8. Deferred (not in MVP)

- Push to a GitHub remote (offer after the first working build).
- Match history, share/export final score, multiple kids/profiles.
- Richer custom `RemoteViews` notification if the standard template feels
  cramped in practice.
