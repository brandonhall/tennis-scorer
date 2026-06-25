package com.tennisscorer.match

import com.tennisscorer.core.DecidingSet
import com.tennisscorer.core.MatchFormat
import com.tennisscorer.core.Player
import kotlinx.serialization.Serializable

/**
 * The persisted match: which format, the two names, who served first, and the
 * ordered list of points (0 = P1 won the point, 1 = P2). Everything shown is
 * derived from this by the core engine, so this is the entire saved state.
 */
@Serializable
data class MatchState(
    val formatKey: String,
    val p1Name: String,
    val p2Name: String,
    val firstServerP1: Boolean,
    val points: List<Int> = emptyList(),
)

data class FormatPreset(val key: String, val label: String, val format: MatchFormat)

object Formats {
    val presets: List<FormatPreset> = listOf(
        FormatPreset("proset_noad", "Pro set · no-ad", MatchFormat.PRO_SET_NO_AD),
        FormatPreset(
            "bo3_full",
            "Best of 3 · full sets",
            MatchFormat(2, 6, true, 6, 7, false, DecidingSet.FULL_SET),
        ),
        FormatPreset(
            "bo3_mtb",
            "Best of 3 · 10-pt 3rd",
            MatchFormat(2, 6, true, 6, 7, false, DecidingSet.MATCH_TIEBREAK, 10),
        ),
        FormatPreset(
            "set6",
            "Single set to 6",
            MatchFormat(1, 6, true, 6, 7, false),
        ),
    )

    val default: FormatPreset = presets.first()

    fun byKey(key: String): FormatPreset = presets.firstOrNull { it.key == key } ?: default
}

fun Player.toCode(): Int = if (this == Player.P1) 0 else 1

fun Int.toPlayer(): Player = if (this == 0) Player.P1 else Player.P2
