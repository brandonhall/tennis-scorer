package com.tennisscorer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tennisscorer.core.Phase
import com.tennisscorer.core.Player
import com.tennisscorer.core.Scoreboard
import com.tennisscorer.match.Formats
import com.tennisscorer.match.MatchRepository
import com.tennisscorer.match.MatchState
import com.tennisscorer.service.ScoringService
import com.tennisscorer.ui.LocalTennisColors
import com.tennisscorer.ui.ThemeMode
import com.tennisscorer.ui.ThemePrefs
import com.tennisscorer.ui.TennisScorerTheme

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MatchRepository.init(this)
        ThemePrefs.init(this)
        maybeRequestNotifPermission()

        setContent {
            val mode by ThemePrefs.mode.collectAsState()
            TennisScorerTheme(mode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
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
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ThemeSwitch() }
        Text(
            "New match",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Field("Your player", p1) { p1 = it }
        Field("Opponent", p2) { p2 = it }

        Label("Format")
        FormatPicker(formatKey) { formatKey = it }

        Label("First serve")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ServeChip(p1.ifBlank { "Your player" }, firstP1) { firstP1 = true }
            ServeChip(p2.ifBlank { "Opponent" }, !firstP1) { firstP1 = false }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onStart(MatchState(formatKey, p1, p2, firstP1)) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Start match", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    val cs = MaterialTheme.colorScheme
    val p1 = state.p1Name.ifBlank { "Player 1" }
    val p2 = state.p2Name.ifBlank { "Player 2" }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ThemeSwitch() }
        Text("$p1 vs $p2", color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Formats.byKey(state.formatKey).label, color = cs.onSurfaceVariant, fontSize = 13.sp)
            phaseTag(scoreboard.phase)?.let {
                Spacer(Modifier.width(8.dp))
                PhasePill(it)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (scoreboard.winner != null) {
            WonBlock(if (scoreboard.winner == Player.P1) p1 else p2, scoreboard, onEnd)
        } else {
            ScoreCard(p1, p2, scoreboard)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("+ $p1", Modifier.weight(1f), onP1)
                BigButton("+ $p2", Modifier.weight(1f), onP2)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Undo", Modifier.weight(1f), onUndo)
                GhostButton("End match", Modifier.weight(1f), onEnd)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Score from the notification too — pull down the shade.",
                color = cs.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScoreCard(p1: String, p2: String, sb: Scoreboard) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            ScoreRow(p1, sb.games.first, sb.points.first, sb.server == Player.P1)
            HorizontalDivider(color = cs.outline.copy(alpha = 0.6f))
            ScoreRow(p2, sb.games.second, sb.points.second, sb.server == Player.P2)
        }
    }
}

@Composable
private fun ScoreRow(name: String, games: Int, points: String, serving: Boolean) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTennisColors.current.accent
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (serving) accent else Color.Transparent),
        )
        Spacer(Modifier.width(12.dp))
        Text(name, color = cs.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("$games", color = cs.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        Text(points, color = if (serving) accent else cs.onBackground, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun WonBlock(winnerName: String, sb: Scoreboard, onEnd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTennisColors.current.accent
    Column(Modifier.fillMaxWidth()) {
        Text("Game, set, match", color = cs.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Text(winnerName, color = accent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("wins", color = cs.onBackground, fontSize = 20.sp)
        Spacer(Modifier.height(18.dp))
        Text(
            sb.completedSets.joinToString("    ") { "${it.first}–${it.second}" },
            color = cs.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("New match", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(64.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GhostButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cs.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurfaceVariant),
    ) {
        Text(text, fontSize = 15.sp)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTennisColors.current.accent
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = cs.outline,
            focusedLabelColor = accent,
            unfocusedLabelColor = cs.onSurfaceVariant,
            cursorColor = accent,
            focusedTextColor = cs.onBackground,
            unfocusedTextColor = cs.onBackground,
        ),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun ServeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary,
            containerColor = cs.surfaceVariant,
            labelColor = cs.onSurfaceVariant,
        ),
        border = null,
    )
}

@Composable
private fun FormatPicker(selectedKey: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTennisColors.current.accent
    var expanded by remember { mutableStateOf(false) }
    val selected = Formats.byKey(selectedKey)
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = cs.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected.label, color = cs.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("▾", color = accent, fontSize = 16.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Formats.presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = { onSelect(preset.key); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwitch() {
    val cs = MaterialTheme.colorScheme
    val accent = LocalTennisColors.current.accent
    val mode by ThemePrefs.mode.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(999.dp),
            color = cs.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(modeLabel(mode), color = cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                Text("▾", color = accent, fontSize = 12.sp)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { m ->
                DropdownMenuItem(text = { Text(modeLabel(m)) }, onClick = { ThemePrefs.set(m); expanded = false })
            }
        }
    }
}

private fun modeLabel(m: ThemeMode): String = when (m) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

@Composable
private fun PhasePill(text: String) {
    val accent = LocalTennisColors.current.accent
    Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun phaseTag(phase: Phase): String? = when (phase) {
    Phase.TIEBREAK -> "Tiebreak"
    Phase.MATCH_TIEBREAK -> "Match tiebreak"
    Phase.DEUCE -> "Deuce"
    else -> null
}
