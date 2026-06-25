# Tennis Scorer — Scoring Engine Implementation Plan (Plan 1 of 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a pure-Kotlin tennis scoring engine that turns an ordered list of points into a full scoreboard (points, games, sets, server, tiebreaks, winner), proven correct by unit tests, defaulting to the user's no-ad pro set.

**Architecture:** Event-sourced and pure. The engine is a single function `ScoringEngine.scoreboard(format, firstServer, points)` that replays the point list over small, individually-tested rule helpers and returns an immutable `Scoreboard`. No Android dependencies, so it runs on the plain JVM via `./gradlew :app:testDebugUnitTest`. Undo in the app layer (Plan 2) is just "drop the last point and recompute."

**Tech Stack:** Kotlin, Android Studio project (so Plan 2 can build on it directly), JUnit4 unit tests (the Android default — no extra deps), Gradle wrapper.

**Scope note:** This is Plan 1 of 2. It delivers a scaffolded project plus a tested engine. Plan 2 adds the notification, foreground service, persistence, and Compose UI and will be written after this engine's API is locked by passing tests. The engine here intentionally supports more formats than the default (ad scoring, standard sets, best-of-3, match tiebreaks) because they fall out of the same general reducer at near-zero extra cost and the spec lists them as selectable presets.

---

## File Structure

Created in this plan (all under `/Users/brandonhall/Code/tennis-scorer`):

- `app/src/main/java/com/tennisscorer/core/Model.kt` — value types: `Player`, `DecidingSet`, `Phase`, `MatchFormat` (+ `PRO_SET_NO_AD` preset), `Scoreboard`. No logic.
- `app/src/main/java/com/tennisscorer/core/Rules.kt` — small pure rule helpers: `gameWinner`, `tiebreakWinner`, `setWinnerByGames`, `gamePointsDisplay`, `serverFor`. Each independently testable.
- `app/src/main/java/com/tennisscorer/core/ScoringEngine.kt` — the `scoreboard(...)` orchestrator that replays points over the helpers.
- `app/src/test/java/com/tennisscorer/core/RulesTest.kt` — unit tests for the helpers.
- `app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt` — unit tests for the orchestrator, including full-match and undo-equivalence cases.

Plus the Android project scaffold generated in Task 1 (Gradle files, manifest, a placeholder activity) — left untouched by later tasks in this plan.

The `core` package has zero Android imports. Keep it that way: it is the unit Plan 2 depends on and the only part that needs exhaustive logic testing.

---

## Task 1: Scaffold the Android project

**Files:**
- Create: the Android project at `/Users/brandonhall/Code/tennis-scorer` (merges with the existing `README.md` and `docs/`).

- [ ] **Step 1: Create the project in Android Studio**

Use Android Studio → New Project → **Empty Activity** (the Compose one) with exactly these settings:

- Name: `Tennis Scorer`
- Package name: `com.tennisscorer`
- Save location: `/Users/brandonhall/Code/tennis-scorer`
- Language: `Kotlin`
- Minimum SDK: `API 26 ("Oreo"; Android 8.0)`
- Build configuration language: `Kotlin DSL (build.gradle.kts)`

This generates the `app` module, Gradle wrapper (`gradlew`), and manifest. It uses Android Studio's bundled JDK 17, sidestepping the machine's PATH `java` 11.

- [ ] **Step 2: Confirm the toolchain and wrapper exist**

Run: `cd /Users/brandonhall/Code/tennis-scorer && ./gradlew --version`
Expected: prints a Gradle version and `JVM: 17.x` (or newer). If it reports JVM 11, set `org.gradle.java.home` in `gradle.properties` to Android Studio's bundled JDK path, or run with `JAVA_HOME` pointing at a JDK 17.

- [ ] **Step 3: Run the default unit test to prove JVM tests work**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; the generated `ExampleUnitTest` passes. This is the loop every later task uses.

- [ ] **Step 4: Commit**

```bash
cd /Users/brandonhall/Code/tennis-scorer
git add -A
git commit -m "chore: scaffold Android project (Empty Compose Activity, minSdk 26)"
```

---

## Task 2: Core value types

**Files:**
- Create: `app/src/main/java/com/tennisscorer/core/Model.kt`
- Test: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tennisscorer/core/RulesTest.kt`:

```kotlin
package com.tennisscorer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesTest {

    @Test
    fun player_other_flips() {
        assertEquals(Player.P2, Player.P1.other())
        assertEquals(Player.P1, Player.P2.other())
    }

    @Test
    fun proSet_preset_has_expected_values() {
        val f = MatchFormat.PRO_SET_NO_AD
        assertEquals(1, f.setsToWin)
        assertEquals(8, f.gamesToWinSet)
        assertEquals(false, f.winByTwoGames)
        assertEquals(7, f.tiebreakAtGames)
        assertEquals(7, f.tiebreakPoints)
        assertEquals(true, f.noAd)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved references `Player`, `MatchFormat`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/tennisscorer/core/Model.kt`:

```kotlin
package com.tennisscorer.core

enum class Player {
    P1, P2;

    fun other(): Player = if (this == P1) P2 else P1
}

enum class DecidingSet { FULL_SET, MATCH_TIEBREAK }

enum class Phase { PLAY, DEUCE, TIEBREAK, MATCH_TIEBREAK, MATCH_OVER }

data class MatchFormat(
    val setsToWin: Int,
    val gamesToWinSet: Int,
    val winByTwoGames: Boolean,
    val tiebreakAtGames: Int?,
    val tiebreakPoints: Int,
    val noAd: Boolean,
    val decidingSet: DecidingSet = DecidingSet.FULL_SET,
    val matchTiebreakPoints: Int = 10,
) {
    companion object {
        val PRO_SET_NO_AD = MatchFormat(
            setsToWin = 1,
            gamesToWinSet = 8,
            winByTwoGames = false,
            tiebreakAtGames = 7,
            tiebreakPoints = 7,
            noAd = true,
        )
    }
}

data class Scoreboard(
    val completedSets: List<Pair<Int, Int>>,
    val games: Pair<Int, Int>,
    val points: Pair<String, String>,
    val phase: Phase,
    val server: Player,
    val winner: Player?,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Model.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add match value types and pro-set preset"
```

---

## Task 3: `gameWinner` (ad and no-ad)

**Files:**
- Create: `app/src/main/java/com/tennisscorer/core/Rules.kt`
- Modify: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

- [ ] **Step 1: Write the failing test** — add these methods inside `RulesTest`:

```kotlin
    @Test
    fun noAd_game_first_to_four_including_deciding_point() {
        assertEquals(Player.P1, gameWinner(4, 0, noAd = true))
        assertEquals(Player.P1, gameWinner(4, 2, noAd = true))
        assertEquals(Player.P1, gameWinner(4, 3, noAd = true)) // 3-3 deciding point
        assertEquals(Player.P2, gameWinner(2, 4, noAd = true))
        assertEquals(null, gameWinner(3, 3, noAd = true))
        assertEquals(null, gameWinner(2, 1, noAd = true))
    }

    @Test
    fun ad_game_requires_margin_of_two() {
        assertEquals(Player.P1, gameWinner(4, 2, noAd = false))
        assertEquals(null, gameWinner(4, 3, noAd = false)) // advantage, not game
        assertEquals(null, gameWinner(4, 4, noAd = false)) // back to deuce
        assertEquals(Player.P1, gameWinner(5, 3, noAd = false))
        assertEquals(Player.P2, gameWinner(3, 5, noAd = false))
        assertEquals(null, gameWinner(3, 3, noAd = false))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved reference `gameWinner`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/tennisscorer/core/Rules.kt`:

```kotlin
package com.tennisscorer.core

internal fun gameWinner(p1: Int, p2: Int, noAd: Boolean): Player? =
    if (noAd) {
        when {
            p1 >= 4 && p1 > p2 -> Player.P1
            p2 >= 4 && p2 > p1 -> Player.P2
            else -> null
        }
    } else {
        when {
            p1 >= 4 && p1 - p2 >= 2 -> Player.P1
            p2 >= 4 && p2 - p1 >= 2 -> Player.P2
            else -> null
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Rules.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add game-winner rule for ad and no-ad scoring"
```

---

## Task 4: `gamePointsDisplay` (0/15/30/40/Ad)

**Files:**
- Modify: `app/src/main/java/com/tennisscorer/core/Rules.kt`
- Modify: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

- [ ] **Step 1: Write the failing test** — add to `RulesTest`:

```kotlin
    @Test
    fun points_display_standard_labels() {
        assertEquals("0" to "0", gamePointsDisplay(0, 0, noAd = true))
        assertEquals("15" to "30", gamePointsDisplay(1, 2, noAd = true))
        assertEquals("40" to "0", gamePointsDisplay(3, 0, noAd = true))
        assertEquals("40" to "40", gamePointsDisplay(3, 3, noAd = true))
    }

    @Test
    fun points_display_deuce_and_advantage_in_ad() {
        assertEquals("40" to "40", gamePointsDisplay(3, 3, noAd = false))
        assertEquals("Ad" to "40", gamePointsDisplay(4, 3, noAd = false))
        assertEquals("40" to "Ad", gamePointsDisplay(3, 4, noAd = false))
        assertEquals("40" to "40", gamePointsDisplay(4, 4, noAd = false))
        assertEquals("Ad" to "40", gamePointsDisplay(5, 4, noAd = false))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved reference `gamePointsDisplay`.

- [ ] **Step 3: Write minimal implementation** — append to `Rules.kt`:

```kotlin
internal fun gamePointsDisplay(p1: Int, p2: Int, noAd: Boolean): Pair<String, String> {
    val names = arrayOf("0", "15", "30", "40")
    if (!noAd && p1 >= 3 && p2 >= 3) {
        return when {
            p1 == p2 -> "40" to "40"
            p1 > p2 -> "Ad" to "40"
            else -> "40" to "Ad"
        }
    }
    val s1 = if (p1 <= 3) names[p1] else "40"
    val s2 = if (p2 <= 3) names[p2] else "40"
    return s1 to s2
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Rules.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add point-display labels with deuce/advantage"
```

---

## Task 5: `tiebreakWinner`

**Files:**
- Modify: `app/src/main/java/com/tennisscorer/core/Rules.kt`
- Modify: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

- [ ] **Step 1: Write the failing test** — add to `RulesTest`:

```kotlin
    @Test
    fun tiebreak_first_to_target_win_by_two() {
        assertEquals(Player.P1, tiebreakWinner(7, 5, target = 7))
        assertEquals(null, tiebreakWinner(7, 6, target = 7))
        assertEquals(Player.P1, tiebreakWinner(8, 6, target = 7))
        assertEquals(Player.P2, tiebreakWinner(6, 8, target = 7))
        assertEquals(Player.P1, tiebreakWinner(10, 8, target = 10))
        assertEquals(null, tiebreakWinner(9, 8, target = 10))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved reference `tiebreakWinner`.

- [ ] **Step 3: Write minimal implementation** — append to `Rules.kt`:

```kotlin
internal fun tiebreakWinner(p1: Int, p2: Int, target: Int): Player? =
    when {
        p1 >= target && p1 - p2 >= 2 -> Player.P1
        p2 >= target && p2 - p1 >= 2 -> Player.P2
        else -> null
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Rules.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add tiebreak-winner rule"
```

---

## Task 6: `setWinnerByGames`

**Files:**
- Modify: `app/src/main/java/com/tennisscorer/core/Rules.kt`
- Modify: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

- [ ] **Step 1: Write the failing test** — add to `RulesTest`:

```kotlin
    @Test
    fun set_win_proset_no_margin_first_to_eight() {
        val f = MatchFormat.PRO_SET_NO_AD
        assertEquals(Player.P1, setWinnerByGames(8, 6, f))
        assertEquals(null, setWinnerByGames(7, 6, f))
        assertEquals(null, setWinnerByGames(7, 7, f))
        assertEquals(Player.P2, setWinnerByGames(3, 8, f))
    }

    @Test
    fun set_win_standard_requires_two_game_margin() {
        val f = MatchFormat(
            setsToWin = 1, gamesToWinSet = 6, winByTwoGames = true,
            tiebreakAtGames = 6, tiebreakPoints = 7, noAd = false,
        )
        assertEquals(Player.P1, setWinnerByGames(6, 4, f))
        assertEquals(null, setWinnerByGames(6, 5, f)) // margin 1
        assertEquals(Player.P1, setWinnerByGames(7, 5, f))
        assertEquals(null, setWinnerByGames(6, 6, f))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved reference `setWinnerByGames`.

- [ ] **Step 3: Write minimal implementation** — append to `Rules.kt`:

```kotlin
internal fun setWinnerByGames(g1: Int, g2: Int, format: MatchFormat): Player? {
    val target = format.gamesToWinSet
    val by2 = format.winByTwoGames
    return when {
        g1 >= target && (!by2 || g1 - g2 >= 2) -> Player.P1
        g2 >= target && (!by2 || g2 - g1 >= 2) -> Player.P2
        else -> null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Rules.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add set-winner-by-games rule"
```

---

## Task 7: `serverFor` (alternation + tiebreak rotation)

**Files:**
- Modify: `app/src/main/java/com/tennisscorer/core/Rules.kt`
- Modify: `app/src/test/java/com/tennisscorer/core/RulesTest.kt`

Rotation rules: server alternates every completed game (a tiebreak counts as one game). Inside a tiebreak the player due to serve serves the first point, then the players alternate serving two points each.

- [ ] **Step 1: Write the failing test** — add to `RulesTest`:

```kotlin
    @Test
    fun server_alternates_each_game() {
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = false, tbPoints = 0))
        assertEquals(Player.P2, serverFor(1, Player.P1, inTiebreak = false, tbPoints = 0))
        assertEquals(Player.P1, serverFor(2, Player.P1, inTiebreak = false, tbPoints = 0))
    }

    @Test
    fun server_tiebreak_one_then_alternating_pairs() {
        // gamesCompleted even -> P1 is due to serve the tiebreak's first point
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 0))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 1))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 2))
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 3))
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 4))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 5))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: FAIL — unresolved reference `serverFor`.

- [ ] **Step 3: Write minimal implementation** — append to `Rules.kt`:

```kotlin
internal fun serverFor(
    gamesCompleted: Int,
    firstServer: Player,
    inTiebreak: Boolean,
    tbPoints: Int,
): Player {
    val due = if (gamesCompleted % 2 == 0) firstServer else firstServer.other()
    if (!inTiebreak) return due
    val offset = if (tbPoints == 0) 0 else ((tbPoints + 1) / 2) % 2
    return if (offset == 0) due else due.other()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.RulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/Rules.kt app/src/test/java/com/tennisscorer/core/RulesTest.kt
git commit -m "feat(core): add server rotation rule"
```

---

## Task 8: `ScoringEngine.scoreboard` orchestrator

**Files:**
- Create: `app/src/main/java/com/tennisscorer/core/ScoringEngine.kt`
- Create: `app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt`. The helpers `game`/`alternate` build readable point sequences (4 uncontested points win a no-ad game).

```kotlin
package com.tennisscorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringEngineTest {

    private val pro = MatchFormat.PRO_SET_NO_AD

    private fun game(winner: Player): List<Player> = List(4) { winner }

    private fun board(points: List<Player>, format: MatchFormat = pro, first: Player = Player.P1) =
        ScoringEngine.scoreboard(format, first, points)

    @Test
    fun empty_match_is_love_all_first_server_serving() {
        val b = board(emptyList())
        assertEquals(0 to 0, b.games)
        assertEquals("0" to "0", b.points)
        assertEquals(Phase.PLAY, b.phase)
        assertEquals(Player.P1, b.server)
        assertNull(b.winner)
    }

    @Test
    fun one_game_advances_games_and_flips_server() {
        val b = board(game(Player.P1))
        assertEquals(1 to 0, b.games)
        assertEquals("0" to "0", b.points)
        assertEquals(Player.P2, b.server)
        assertNull(b.winner)
    }

    @Test
    fun points_show_within_a_game() {
        val b = board(listOf(Player.P1, Player.P1, Player.P2))
        assertEquals("30" to "15", b.points)
        assertEquals(0 to 0, b.games)
        assertEquals(Phase.PLAY, b.phase)
    }

    @Test
    fun proset_eight_love_wins_the_match() {
        val points = (1..8).flatMap { game(Player.P1) }
        val b = board(points)
        assertEquals(Player.P1, b.winner)
        assertEquals(Phase.MATCH_OVER, b.phase)
        assertEquals(listOf(8 to 0), b.completedSets)
    }

    @Test
    fun proset_reaches_tiebreak_at_seven_all_and_records_eight_seven() {
        // 14 games alternating -> 7-7, then P1 wins a 7-0 tiebreak
        val toSevenAll = (1..7).flatMap { game(Player.P1) + game(Player.P2) }
        val tiebreak = List(7) { Player.P1 }
        val b = board(toSevenAll + tiebreak)
        assertEquals(Player.P1, b.winner)
        assertEquals(Phase.MATCH_OVER, b.phase)
        assertEquals(listOf(8 to 7), b.completedSets)
    }

    @Test
    fun proset_tiebreak_in_progress_shows_numeric_points_and_phase() {
        val toSevenAll = (1..7).flatMap { game(Player.P1) + game(Player.P2) }
        val b = board(toSevenAll + listOf(Player.P1, Player.P1, Player.P2))
        assertEquals(Phase.TIEBREAK, b.phase)
        assertEquals(7 to 7, b.games)
        assertEquals("2" to "1", b.points)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.ScoringEngineTest"`
Expected: FAIL — unresolved reference `ScoringEngine`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/tennisscorer/core/ScoringEngine.kt`:

```kotlin
package com.tennisscorer.core

object ScoringEngine {

    fun scoreboard(format: MatchFormat, firstServer: Player, points: List<Player>): Scoreboard {
        var sets1 = 0
        var sets2 = 0
        val completedSets = mutableListOf<Pair<Int, Int>>()
        var g1 = 0
        var g2 = 0
        var p1 = 0
        var p2 = 0
        var gamesCompleted = 0
        var winner: Player? = null

        fun decidingMatchTiebreak(): Boolean =
            format.decidingSet == DecidingSet.MATCH_TIEBREAK &&
                format.setsToWin >= 2 &&
                sets1 == format.setsToWin - 1 &&
                sets2 == format.setsToWin - 1

        fun inSetTiebreak(): Boolean =
            format.tiebreakAtGames != null &&
                g1 == format.tiebreakAtGames &&
                g2 == format.tiebreakAtGames

        for (pt in points) {
            if (winner != null) break

            when {
                decidingMatchTiebreak() -> {
                    if (pt == Player.P1) p1++ else p2++
                    val w = tiebreakWinner(p1, p2, format.matchTiebreakPoints)
                    if (w != null) {
                        completedSets.add(p1 to p2)
                        if (w == Player.P1) sets1++ else sets2++
                        winner = w
                    }
                }

                inSetTiebreak() -> {
                    if (pt == Player.P1) p1++ else p2++
                    val w = tiebreakWinner(p1, p2, format.tiebreakPoints)
                    if (w != null) {
                        if (w == Player.P1) g1 += 1 else g2 += 1
                        completedSets.add(g1 to g2)
                        if (w == Player.P1) sets1++ else sets2++
                        gamesCompleted++
                        g1 = 0; g2 = 0; p1 = 0; p2 = 0
                        if (sets1 == format.setsToWin || sets2 == format.setsToWin) winner = w
                    }
                }

                else -> {
                    if (pt == Player.P1) p1++ else p2++
                    val gw = gameWinner(p1, p2, format.noAd)
                    if (gw != null) {
                        if (gw == Player.P1) g1++ else g2++
                        p1 = 0; p2 = 0
                        gamesCompleted++
                        val sw = setWinnerByGames(g1, g2, format)
                        if (sw != null) {
                            completedSets.add(g1 to g2)
                            if (sw == Player.P1) sets1++ else sets2++
                            g1 = 0; g2 = 0
                            if (sets1 == format.setsToWin || sets2 == format.setsToWin) winner = sw
                        }
                    }
                }
            }
        }

        val inTb = winner == null && (decidingMatchTiebreak() || inSetTiebreak())
        val phase = when {
            winner != null -> Phase.MATCH_OVER
            decidingMatchTiebreak() -> Phase.MATCH_TIEBREAK
            inSetTiebreak() -> Phase.TIEBREAK
            p1 >= 3 && p2 >= 3 -> Phase.DEUCE
            else -> Phase.PLAY
        }
        val pointsDisplay = when {
            winner != null -> "" to ""
            inTb -> p1.toString() to p2.toString()
            else -> gamePointsDisplay(p1, p2, format.noAd)
        }
        val server = serverFor(gamesCompleted, firstServer, inTb, p1 + p2)

        return Scoreboard(
            completedSets = completedSets.toList(),
            games = g1 to g2,
            points = pointsDisplay,
            phase = phase,
            server = server,
            winner = winner,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.ScoringEngineTest"`
Expected: PASS (all six cases).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/tennisscorer/core/ScoringEngine.kt app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt
git commit -m "feat(core): add scoreboard orchestrator with pro-set coverage"
```

---

## Task 9: Format coverage + undo-equivalence

This task adds no production code unless a test fails. It proves the general reducer handles ad scoring, standard sets, best-of-3 with a full deciding set, and best-of-3 with a 10-point match tiebreak, and that recomputing after dropping the last point matches the prior state (the basis for app-layer undo). If any test fails, fix `ScoringEngine.kt` or the relevant helper, then re-run.

**Files:**
- Modify: `app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt`

- [ ] **Step 1: Write the tests** — add to `ScoringEngineTest`:

```kotlin
    private val standardSet = MatchFormat(
        setsToWin = 1, gamesToWinSet = 6, winByTwoGames = true,
        tiebreakAtGames = 6, tiebreakPoints = 7, noAd = false,
    )

    private val bestOf3FullSets = MatchFormat(
        setsToWin = 2, gamesToWinSet = 6, winByTwoGames = true,
        tiebreakAtGames = 6, tiebreakPoints = 7, noAd = false,
        decidingSet = DecidingSet.FULL_SET,
    )

    private val bestOf3MatchTb = MatchFormat(
        setsToWin = 2, gamesToWinSet = 6, winByTwoGames = true,
        tiebreakAtGames = 6, tiebreakPoints = 7, noAd = false,
        decidingSet = DecidingSet.MATCH_TIEBREAK, matchTiebreakPoints = 10,
    )

    private fun adGame(winner: Player): List<Player> = List(4) { winner } // 4-0 wins an ad game too

    @Test
    fun ad_game_deuce_then_advantage_then_win() {
        // 3-3 deuce, P1 takes advantage, P2 levels, P1 wins two straight
        val seq = listOf(
            Player.P1, Player.P1, Player.P1, // 40-0
            Player.P2, Player.P2, Player.P2, // 40-40 deuce
            Player.P1,                       // Ad P1
            Player.P2,                       // back to deuce
            Player.P1, Player.P1,            // P1 wins
        )
        val b = board(seq, format = standardSet)
        assertEquals(1 to 0, b.games)
        assertEquals(Player.P2, b.server)
    }

    @Test
    fun standard_set_can_end_seven_five() {
        // P1 wins 7, P2 wins 5, no 6-6 tiebreak reached: order so it ends 7-5
        val games = mutableListOf<Player>()
        repeat(5) { games += adGame(Player.P1); games += adGame(Player.P2) } // 5-5
        games += adGame(Player.P1) // 6-5
        games += adGame(Player.P1) // 7-5 win
        val b = board(games, format = standardSet)
        assertEquals(Player.P1, b.winner)
        assertEquals(listOf(7 to 5), b.completedSets)
    }

    @Test
    fun best_of_three_full_sets_needs_two_sets() {
        // P1 wins set 1 (6-0), P2 wins set 2 (6-0), P1 wins set 3 (6-0)
        val set6 = { w: Player -> (1..6).flatMap { adGame(w) } }
        val b = board(set6(Player.P1) + set6(Player.P2) + set6(Player.P1), format = bestOf3FullSets)
        assertEquals(Player.P1, b.winner)
        assertEquals(listOf(6 to 0, 0 to 6, 6 to 0), b.completedSets)
    }

    @Test
    fun best_of_three_with_match_tiebreak_decider() {
        val set6 = { w: Player -> (1..6).flatMap { adGame(w) } }
        val matchTb = List(10) { Player.P1 } // 10-0 match tiebreak
        val b = board(set6(Player.P1) + set6(Player.P2) + matchTb, format = bestOf3MatchTb)
        assertEquals(Player.P1, b.winner)
        assertEquals(listOf(6 to 0, 0 to 6, 10 to 0), b.completedSets)
    }

    @Test
    fun match_tiebreak_in_progress_uses_match_tiebreak_phase() {
        val set6 = { w: Player -> (1..6).flatMap { adGame(w) } }
        val b = board(set6(Player.P1) + set6(Player.P2) + listOf(Player.P1, Player.P2), format = bestOf3MatchTb)
        assertEquals(Phase.MATCH_TIEBREAK, b.phase)
        assertEquals("1" to "1", b.points)
    }

    @Test
    fun dropping_last_point_recomputes_the_prior_scoreboard() {
        // Undo basis: scoreboard(seq minus last) equals scoreboard at that earlier state.
        val seq = (1..7).flatMap { game(Player.P1) + game(Player.P2) } +
            listOf(Player.P1, Player.P1, Player.P2) // mid-tiebreak 2-1
        val full = board(seq)
        val undone = board(seq.dropLast(1)) // tiebreak 2-0
        assertEquals(Phase.TIEBREAK, undone.phase)
        assertEquals("2" to "0", undone.points)
        // and the full board is the next point along
        assertEquals("2" to "1", full.points)
    }
```

- [ ] **Step 2: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.tennisscorer.core.ScoringEngineTest"`
Expected: PASS. If a case fails, the failure message points at the rule to fix (e.g. a wrong `completedSets` entry → orchestrator set-recording; wrong server → `serverFor`). Fix and re-run until green.

- [ ] **Step 3: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `RulesTest` and `ScoringEngineTest` all pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/test/java/com/tennisscorer/core/ScoringEngineTest.kt app/src/main/java/com/tennisscorer/core
git commit -m "test(core): cover ad/standard/best-of-3/match-tiebreak and undo equivalence"
```

---

## Self-Review (completed during authoring)

**Spec coverage** — every §3 scoring requirement maps to a task: points 0/15/30/40 + deuce/ad (Tasks 4, 8, 9); no-ad deciding point (Task 3); games + win-by-two and no-margin sets (Task 6, 9); set tiebreak at the configured games with the winner credited the extra game (Task 8 — pro-set 8–7); match tiebreak decider (Task 9); serve alternation + tiebreak rotation (Task 7); the `Scoreboard` display surface (Task 8). §7 test vectors are realized in Tasks 8–9. Persistence, the notification, the service, and the Compose UI are deliberately deferred to Plan 2.

**Placeholder scan** — no TBD/TODO; every code step is complete and compilable; no "add error handling"-style hand-waves.

**Type consistency** — `gameWinner`, `tiebreakWinner`, `setWinnerByGames`, `gamePointsDisplay`, `serverFor`, and `ScoringEngine.scoreboard` keep identical signatures between their defining task and their use in the orchestrator; `Scoreboard` field names (`completedSets`, `games`, `points`, `phase`, `server`, `winner`) are used consistently in the tests.

---

## Next: Plan 2 (Android shell)

Written after this engine is green. It will cover: `MatchRepository` (active match + JSON persistence on every change, with undo = drop last point), the `ScoringService` foreground service and `NotificationCompat` rendering of `Scoreboard`, the `ScoreActionReceiver` for the `+P1 / +P2 / Undo` actions, the Compose setup screen + live mirror, the `POST_NOTIFICATIONS` permission flow, the `specialUse` foreground-service-type declaration, and the `BootReceiver` restore. Manual on-device verification (tap loop, swipe-resistance, app-kill and reboot recovery) closes it out.
