package com.tennisscorer.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tennisscorer.core.Player
import com.tennisscorer.match.MatchRepository

/**
 * Handles the notification's +P1 / +P2 / Undo buttons. Applies the command to
 * the repository (which persists), then re-posts the notification from the new
 * scoreboard. No activity is launched, so this is safe under Android 12+
 * background-launch restrictions.
 */
class ScoreActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.tennisscorer.action.SCORE"
        const val EXTRA_CMD = "cmd"
        const val CMD_P1 = "P1"
        const val CMD_P2 = "P2"
        const val CMD_UNDO = "UNDO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        MatchRepository.init(context)
        when (intent.getStringExtra(EXTRA_CMD)) {
            CMD_P1 -> MatchRepository.addPoint(Player.P1)
            CMD_P2 -> MatchRepository.addPoint(Player.P2)
            CMD_UNDO -> MatchRepository.undo()
        }

        val state = MatchRepository.state.value ?: return
        Notifications.ensureChannel(context)
        val notif = Notifications.build(context, state, MatchRepository.scoreboardOf(state))
        context.getSystemService(NotificationManager::class.java)
            .notify(Notifications.NOTIF_ID, notif)
    }
}
