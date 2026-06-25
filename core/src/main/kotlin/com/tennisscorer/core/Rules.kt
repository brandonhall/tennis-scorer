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

internal fun tiebreakWinner(p1: Int, p2: Int, target: Int): Player? =
    when {
        p1 >= target && p1 - p2 >= 2 -> Player.P1
        p2 >= target && p2 - p1 >= 2 -> Player.P2
        else -> null
    }

internal fun setWinnerByGames(g1: Int, g2: Int, format: MatchFormat): Player? {
    val target = format.gamesToWinSet
    val by2 = format.winByTwoGames
    return when {
        g1 >= target && (!by2 || g1 - g2 >= 2) -> Player.P1
        g2 >= target && (!by2 || g2 - g1 >= 2) -> Player.P2
        else -> null
    }
}

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
