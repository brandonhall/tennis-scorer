package com.tennisscorer.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.tennisscorer.match.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and the ongoing notification present for the duration
 * of a match. Observes the repository and re-posts the notification on every
 * change; tears itself down when the match ends (state becomes null).
 */
class ScoringService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MatchRepository.init(this)
        Notifications.ensureChannel(this)

        val state = MatchRepository.state.value
        if (state == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = Notifications.build(this, state, MatchRepository.scoreboardOf(state))
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.NOTIF_ID, notif, type)

        observe()
        return START_STICKY
    }

    private fun observe() {
        if (observing) return
        observing = true
        scope.launch {
            MatchRepository.state.collectLatest { s ->
                if (s == null) {
                    ServiceCompat.stopForeground(this@ScoringService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val notif = Notifications.build(this@ScoringService, s, MatchRepository.scoreboardOf(s))
                    getSystemService(NotificationManager::class.java)
                        .notify(Notifications.NOTIF_ID, notif)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        observing = false
        super.onDestroy()
    }
}
