package com.tennisscorer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringEngineTest {

    private val pro = MatchFormat.PRO_SET_NO_AD

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

    private fun game(winner: Player): List<Player> = List(4) { winner }
    private fun adGame(winner: Player): List<Player> = List(4) { winner }

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

    @Test
    fun ad_game_deuce_then_advantage_then_win() {
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
        val seq = (1..7).flatMap { game(Player.P1) + game(Player.P2) } +
            listOf(Player.P1, Player.P1, Player.P2) // mid-tiebreak 2-1
        val full = board(seq)
        val undone = board(seq.dropLast(1)) // tiebreak 2-0
        assertEquals(Phase.TIEBREAK, undone.phase)
        assertEquals("2" to "0", undone.points)
        assertEquals("2" to "1", full.points)
    }
}
