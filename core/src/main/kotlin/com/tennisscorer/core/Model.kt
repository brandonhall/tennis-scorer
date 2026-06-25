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
