package com.tennisscorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tennisscorer.core.Player
import com.tennisscorer.core.Scoreboard
import com.tennisscorer.match.Formats
import com.tennisscorer.match.MatchRepository
import com.tennisscorer.match.MatchState
import com.tennisscorer.service.ScoringService

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MatchRepository.init(this)
        maybeRequestNotifPermission()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by MatchRepository.state.collectAsState()
                    val s = state
                    if (s == null) {
                        SetupScreen(onStart = ::startMatch)
                    } else {
                        LiveScreen(
                            state = s,
                            scoreboard = MatchRepository.scoreboardOf(s),
                            onP1 = { MatchRepository.addPoint(Player.P1) },
                            onP2 = { MatchRepository.addPoint(Player.P2) },
                            onUndo = { MatchRepository.undo() },
                            onEnd = { MatchRepository.end() },
                        )
                    }
                }
            }
        }
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMatch(state: MatchState) {
        MatchRepository.start(state)
        val svc = Intent(this, ScoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }
}

@Composable
private fun SetupScreen(onStart: (MatchState) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var formatKey by remember { mutableStateOf(Formats.default.key) }
    var firstP1 by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("New match", fontSize = 24.sp, fontWeight = FontWeight.Medium)

        OutlinedTextField(
            value = p1,
            onValueChange = { p1 = it },
            label = { Text("Your player") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = p2,
            onValueChange = { p2 = it },
            label = { Text("Opponent") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Format", fontWeight = FontWeight.Medium)
        FormatPicker(selectedKey = formatKey, onSelect = { formatKey = it })

        Text("First serve", fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = firstP1,
                onClick = { firstP1 = true },
                label = { Text(p1.ifBlank { "Your player" }) },
            )
            FilterChip(
                selected = !firstP1,
                onClick = { firstP1 = false },
                label = { Text(p2.ifBlank { "Opponent" }) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onStart(MatchState(formatKey, p1, p2, firstP1)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start match")
        }
    }
}

@Composable
private fun FormatPicker(selectedKey: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = Formats.byKey(selectedKey)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Formats.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        onSelect(preset.key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LiveScreen(
    state: MatchState,
    scoreboard: Scoreboard,
    onP1: () -> Unit,
    onP2: () -> Unit,
    onUndo: () -> Unit,
    onEnd: () -> Unit,
) {
    val p1 = state.p1Name.ifBlank { "Player 1" }
    val p2 = state.p2Name.ifBlank { "Player 2" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("$p1 vs $p2", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(
            Formats.byKey(state.formatKey).label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        if (scoreboard.winner != null) {
            val w = if (scoreboard.winner == Player.P1) p1 else p2
            Spacer(Modifier.height(8.dp))
            Text("$w wins", fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Text(
                scoreboard.completedSets.joinToString(", ") { "${it.first}-${it.second}" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("New match") }
        } else {
            Spacer(Modifier.height(4.dp))
            PlayerRow(p1, scoreboard, Player.P1)
            PlayerRow(p2, scoreboard, Player.P2)
            if (scoreboard.completedSets.isNotEmpty()) {
                Text(
                    "Sets: " + scoreboard.completedSets.joinToString(", ") { "${it.first}-${it.second}" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = onP1, modifier = Modifier.weight(1f)) { Text("+ $p1") }
                Button(onClick = onP2, modifier = Modifier.weight(1f)) { Text("+ $p2") }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("Undo") }
                OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f)) { Text("End match") }
            }
            Text(
                "Scoring lives in your notification shade too — pull it down.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PlayerRow(name: String, sb: Scoreboard, who: Player) {
    val serving = sb.server == who
    val (g1, g2) = sb.games
    val (pt1, pt2) = sb.points
    val games = if (who == Player.P1) g1 else g2
    val pts = if (who == Player.P1) pt1 else pt2

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (serving) "● " else "   ",
            color = MaterialTheme.colorScheme.primary,
        )
        Text(name, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text("$games", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(48.dp))
        Text(pts, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(56.dp))
    }
}
