package com.tennisscorer.match

import android.content.Context
import com.tennisscorer.core.Player
import com.tennisscorer.core.Scoreboard
import com.tennisscorer.core.ScoringEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Process-wide single source of truth for the active match. Every mutation
 * recomputes nothing eagerly (the engine is cheap) but persists the point list
 * to disk, so an OS kill, crash, or reboot can restore mid-match.
 */
object MatchRepository {

    private const val FILE_NAME = "match.json"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var appContext: Context

    private val _state = MutableStateFlow<MatchState?>(null)
    val state: StateFlow<MatchState?> = _state.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        load()
    }

    private fun file(): File = File(appContext.filesDir, FILE_NAME)

    private fun load() {
        val f = file()
        if (!f.exists()) return
        runCatching { json.decodeFromString(MatchState.serializer(), f.readText()) }
            .onSuccess { _state.value = it }
    }

    private fun persist() {
        val f = file()
        val s = _state.value
        if (s == null) {
            f.delete()
        } else {
            runCatching { f.writeText(json.encodeToString(MatchState.serializer(), s)) }
        }
    }

    fun start(state: MatchState) {
        _state.value = state
        persist()
    }

    fun addPoint(player: Player) {
        val s = _state.value ?: return
        if (scoreboardOf(s).winner != null) return
        _state.value = s.copy(points = s.points + player.toCode())
        persist()
    }

    fun undo() {
        val s = _state.value ?: return
        if (s.points.isEmpty()) return
        _state.value = s.copy(points = s.points.dropLast(1))
        persist()
    }

    fun end() {
        _state.value = null
        persist()
    }

    fun scoreboardOf(s: MatchState): Scoreboard {
        val format = Formats.byKey(s.formatKey).format
        val first = if (s.firstServerP1) Player.P1 else Player.P2
        return ScoringEngine.scoreboard(format, first, s.points.map { it.toPlayer() })
    }
}
