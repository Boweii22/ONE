package com.oneglobal.billboard.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.oneglobal.billboard.ui.theme.*
import com.oneglobal.billboard.model.MainTab

val LocalTourTargets = compositionLocalOf<MutableMap<String, Rect>> { mutableMapOf() }

private data class TourStep(val target: String, val tab: MainTab, val title: String, val body: String)

@Composable
fun GuidedTourHost(
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val prefs = LocalContext.current.getSharedPreferences("one_tour", Context.MODE_PRIVATE)
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var showing by remember { mutableStateOf(enabled && !prefs.getBoolean("seen", false)) }
    var step by remember { mutableIntStateOf(0) }
    val targets = remember { mutableStateMapOf<String, Rect>() }
    val steps = listOf(
        TourStep("LIVE", MainTab.LIVE, "ONE screen. Everyone watching.", "This is the live global screen. Watch its owner and message here."),
        TourStep("TAKE", MainTab.LIVE, "Take the screen.", "When it's open, tap here to challenge the current owner and broadcast your own message to everyone watching."),
        TourStep("REACT", MainTab.LIVE, "React live.", "React once with each emoji per reign — fire, respect, 100, watch or rocket. Every reaction counts toward the live total."),
        TourStep("LIBRARY", MainTab.LIBRARY, "Choose your words.", "Create a message and have it approved. Choose it when you challenge the current owner."),
        TourStep("HALL", MainTab.HALL, "Every reign counts.", "Compare real takeover counts and reigns. Switch between Today and All time to see how you rank."),
        TourStep("YOU", MainTab.YOU, "Make it yours.", "Set your photo and optional country. Protect your handle with Google. Replay this guide anytime from How to Play."),
        TourStep("WALLET", MainTab.YOU, "Your ONE Vault.", "This is your ONE Credits balance. Spend one credit to skip your cooldown, or tap + to get more."),
    )
    fun finish() { showing = false; prefs.edit().putBoolean("seen", true).apply() }
    LaunchedEffect(showing, step) {
        if (showing && currentTab != steps[step].tab) onSelectTab(steps[step].tab)
    }
    CompositionLocalProvider(LocalTourTargets provides targets) {
        Box(Modifier.fillMaxSize()) {
            content { step = 0; showing = true }
            if (showing) {
                BackHandler { finish() }
                val item = steps[step]
                val target = targets[item.target]
                // Consume touches outside the card so the tour cannot trigger a purchase or challenge.
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent, onClick = {}) {
                    Canvas(Modifier.fillMaxSize()) {
                        val mask = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            target?.let { addRect(it.inflate(3.dp.toPx())) }
                        }
                        drawPath(mask, Color.Black.copy(alpha = .86f))
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 110.dp).widthIn(max = 440.dp).fillMaxWidth().background(InkRaised, RoundedCornerShape(18.dp)).padding(22.dp)) {
                    Text("${step + 1} / ${steps.size}  ·  YOUR FIRST REIGN", color = Acid, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(item.title, color = Paper, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text(item.body, color = Paper, fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show first-time tips", color = Muted, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.edit().putBoolean("enabled", it).apply() })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { finish() }) { Text("Skip", color = Paper) }
                        Button(onClick = { if (step == steps.lastIndex) finish() else step++ }, colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink)) { Text(if (step == steps.lastIndex) "Let's play" else "Next →") }
                    }
                }
            }
        }
    }
}
