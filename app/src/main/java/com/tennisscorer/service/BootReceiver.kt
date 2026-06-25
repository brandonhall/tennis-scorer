package com.tennisscorer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tennisscorer.match.MatchRepository

/** Restores the scoring notification after a reboot if a match was in progress. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        MatchRepository.init(context)
        if (MatchRepository.state.value == null) return

        val svc = Intent(context, ScoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
