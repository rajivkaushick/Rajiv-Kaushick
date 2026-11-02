@file:OptIn(ExperimentalMaterial3Api::class)

package com.dartpractice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ---------- Speech ----------
class Speaker(context: Context) {
    var enabled = true
    private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US)   // falls back to device default if unavailable
                ready = true
            }
        }
    }

    fun say(text: String, queue: Boolean = false) {
        if (!enabled || !ready) return
        tts.speak(text, if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH, null, "dart")
    }

    fun shutdown() { tts.stop(); tts.shutdown() }
}

fun scoreText(name: String, s: Int) = when (s) {
    0 -> "$name, no score"
    180 -> "$name, one hundred and eighty!"
    else -> "$name scored $s"
}

// ---------- Game model ----------
class Player(val name: String) {
    val scores = mutableStateListOf<Int>()   // one entry = total of 3 darts
    val total get() = scores.sum()
}

class Game(val names: List<String>, val rounds: Int) {
    val players = names.map { Player(it) }
    private val played get() = players.sumOf { it.scores.size }
    val finished get() = played >= players.size * rounds
    val current get() = players[played % players.size]
    val round get() = played / players.size + 1
    val canUndo get() = played > 0
    fun add(score: Int) { current.scores.add(score) }
    fun undo() {
        if (played > 0) players[(played - 1) % players.size].scores.let { it.removeAt(it.lastIndex) }
    }
}

// ---------- Activity ----------
class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                DartApp(speaker)
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}

@Composable
fun DartApp(speaker: Speaker) {
    var game by remember { mutableStateOf<Game?>(null) }
    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().systemBarsPadding().imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                val g = game
                when {
                    g == null -> SetupScreen { names, rounds -> game = Game(names, rounds) }
                    g.finished -> ResultScreen(
                        g, speaker,
                        onRematch = { game = Game(g.names, g.rounds) },
                        onNew = { game = null }
                    )
                    else -> PlayScreen(g, speaker, onExit = { game = null })
                }
            }
        }
    }
}

// ---------- Screens ----------
@Composable
fun SetupScreen(onStart: (List<String>, Int) -> Unit) {
    var count by remember { mutableIntStateOf(2) }
    var rounds by remember { mutableIntStateOf(5) }
    val names = remember { mutableStateListOf("", "", "", "", "", "") }

    Text("🎯 Dart Practice", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))

    Text("Number of players (max 6)", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (count > 1) count-- }) { Text("−", fontSize = 22.sp) }
        Text("$count", Modifier.padding(horizontal = 24.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(onClick = { if (count < 6) count++ }) { Text("+", fontSize = 22.sp) }
    }

    Spacer(Modifier.height(12.dp))
    for (i in 0 until count) {
        OutlinedTextField(
            value = names[i],
            onValueChange = { names[i] = it.take(16) },
            label = { Text("Player ${i + 1} name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    Spacer(Modifier.height(20.dp))
    Text("Rounds (3 darts each)", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(3, 5, 10, 15).forEach { r ->
            FilterChip(selected = rounds == r, onClick = { rounds = r }, label = { Text("$r") })
        }
    }

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = {
            onStart(List(count) { i -> names[i].trim().ifEmpty { "Player ${i + 1}" } }, rounds)
        },
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Start game", fontSize = 18.sp) }
}

@Composable
fun PlayScreen(game: Game, speaker: Speaker, onExit: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var voice by remember { mutableStateOf(speaker.enabled) }
    val current = game.current

    fun submit() {
        val v = input.trim().toIntOrNull()
        if (v == null || v !in 0..180) {
            error = "Enter a score from 0 to 180"
            return
        }
        error = null
        input = ""
        game.add(v)
        speaker.say(scoreText(current.name, v))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Round ${game.round} of ${game.rounds}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text("Voice", Modifier.padding(end = 8.dp))
        Switch(checked = voice, onCheckedChange = { voice = it; speaker.enabled = it })
    }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Now throwing", fontSize = 14.sp)
            Text(current.name, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(3); error = null },
                label = { Text("Total of 3 darts (0–180)") },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { game.undo(); error = null },
                    enabled = game.canUndo,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("Undo") }
                Button(onClick = { submit() }, modifier = Modifier.weight(2f).height(48.dp)) {
                    Text("Submit score", fontSize = 16.sp)
                }
            }
        }
    }

    Text("Scoreboard", style = MaterialTheme.typography.titleMedium)
    game.players.forEach { p ->
        val isCurrent = p === current
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (p.scores.isEmpty()) "No throws yet" else p.scores.joinToString(" · "),
                        fontSize = 13.sp
                    )
                }
                Text("${p.total}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    TextButton(onClick = onExit, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("End game") }
}

@Composable
fun ResultScreen(game: Game, speaker: Speaker, onRematch: () -> Unit, onNew: () -> Unit) {
    val ranked = game.players.sortedByDescending { it.total }
    val top = ranked.first().total
    val winners = ranked.filter { it.total == top }

    LaunchedEffect(Unit) {
        val msg = if (winners.size == 1) "${winners[0].name} wins with $top points"
        else "It's a tie between ${winners.joinToString(" and ") { it.name }} with $top points"
        speaker.say(msg, queue = true)
    }

    Text("🏆 Game over", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        if (winners.size == 1) "${winners[0].name} wins with $top points!"
        else "Tie: ${winners.joinToString(" & ") { it.name }} ($top points)",
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(Modifier.height(16.dp))

    ranked.forEachIndexed { i, p ->
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (p.total == top) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Best turn: ${p.scores.maxOrNull() ?: 0}", fontSize = 13.sp)
                }
                Text("${p.total}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    Button(onClick = onRematch, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Rematch (same players)") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("New game") }
}
