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

    @Test
    fun tiebreak_first_to_target_win_by_two() {
        assertEquals(Player.P1, tiebreakWinner(7, 5, target = 7))
        assertEquals(null, tiebreakWinner(7, 6, target = 7))
        assertEquals(Player.P1, tiebreakWinner(8, 6, target = 7))
        assertEquals(Player.P2, tiebreakWinner(6, 8, target = 7))
        assertEquals(Player.P1, tiebreakWinner(10, 8, target = 10))
        assertEquals(null, tiebreakWinner(9, 8, target = 10))
    }

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

    @Test
    fun server_alternates_each_game() {
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = false, tbPoints = 0))
        assertEquals(Player.P2, serverFor(1, Player.P1, inTiebreak = false, tbPoints = 0))
        assertEquals(Player.P1, serverFor(2, Player.P1, inTiebreak = false, tbPoints = 0))
    }

    @Test
    fun server_tiebreak_one_then_alternating_pairs() {
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 0))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 1))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 2))
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 3))
        assertEquals(Player.P1, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 4))
        assertEquals(Player.P2, serverFor(0, Player.P1, inTiebreak = true, tbPoints = 5))
    }
}
