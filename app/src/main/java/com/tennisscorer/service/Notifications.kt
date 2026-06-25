package com.tennisscorer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tennisscorer.MainActivity
import com.tennisscorer.R
import com.tennisscorer.core.Phase
import com.tennisscorer.core.Player
import com.tennisscorer.core.Scoreboard
import com.tennisscorer.match.MatchState
import com.tennisscorer.ui.ThemeMode
import com.tennisscorer.ui.ThemePrefs

/** Maps a [Scoreboard] into the ongoing, interactive notification. */
object Notifications {

    const val CHANNEL_ID = "scoring"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Match scoring",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun build(context: Context, state: MatchState, sb: Scoreboard): Notification {
        val p1 = state.p1Name.ifBlank { "Player 1" }
        val p2 = state.p2Name.ifBlank { "Player 2" }
        ThemePrefs.init(context)
        val bg = if (resolveDark(context)) 0xFF0D0F0E.toInt() else 0xFFEAEEDC.toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_score)
            .setContentTitle("$p1 vs $p2")
            .setContentText(collapsed(sb, p1, p2))
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded(sb, p1, p2)))
            .setOngoing(sb.winner == null)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(bg)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (sb.winner == null) {
            builder.addAction(0, "+ ${shortName(p1)}", action(context, ScoreActionReceiver.CMD_P1, 1))
            builder.addAction(0, "+ ${shortName(p2)}", action(context, ScoreActionReceiver.CMD_P2, 2))
            builder.addAction(0, "Undo", action(context, ScoreActionReceiver.CMD_UNDO, 3))
        } else {
            builder.addAction(0, "New match", openApp(context))
        }
        return builder.build()
    }

    private fun resolveDark(context: Context): Boolean = when (ThemePrefs.mode.value) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun shortName(n: String): String = if (n.length <= 8) n else n.take(7) + "…"

    private fun dot(sb: Scoreboard, who: Player): String =
        if (sb.winner == null && sb.server == who) "● " else ""

    private fun collapsed(sb: Scoreboard, p1: String, p2: String): String {
        if (sb.winner != null) {
            val w = if (sb.winner == Player.P1) p1 else p2
            return "$w wins · ${setsSummary(sb)}"
        }
        val (g1, g2) = sb.games
        val (pt1, pt2) = sb.points
        val label = when (sb.phase) {
            Phase.TIEBREAK -> "Tiebreak"
            Phase.MATCH_TIEBREAK -> "Match TB"
            else -> "Games"
        }
        return "$label $g1-$g2 · $pt1-$pt2"
    }

    private fun expanded(sb: Scoreboard, p1: String, p2: String): String {
        if (sb.winner != null) {
            val w = if (sb.winner == Player.P1) p1 else p2
            return "$w wins\n${setsSummary(sb)}"
        }
        val (g1, g2) = sb.games
        val (pt1, pt2) = sb.points
        val sets = if (sb.completedSets.isEmpty()) "" else "\nSets: ${setsSummary(sb)}"
        return "${dot(sb, Player.P1)}$p1   games $g1   pts $pt1\n" +
            "${dot(sb, Player.P2)}$p2   games $g2   pts $pt2$sets"
    }

    private fun setsSummary(sb: Scoreboard): String =
        sb.completedSets.joinToString(", ") { "${it.first}-${it.second}" }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun action(context: Context, cmd: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ScoreActionReceiver::class.java)
            .setAction(ScoreActionReceiver.ACTION)
            .putExtra(ScoreActionReceiver.EXTRA_CMD, cmd)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
