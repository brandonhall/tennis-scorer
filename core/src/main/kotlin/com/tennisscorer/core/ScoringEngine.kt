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
