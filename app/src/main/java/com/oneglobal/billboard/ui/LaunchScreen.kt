package com.oneglobal.billboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneglobal.billboard.R
import com.oneglobal.billboard.ui.theme.*

@Composable
fun LaunchScreen() {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        val markSize = (maxHeight.value * .32f).coerceIn(88f, 210f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Text("ONE PERSON. ONE GLOBAL SCREEN.", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
                alpha = arrival.value
                translationY = (1f - arrival.value) * 40f
                scaleX = .88f + .12f * arrival.value
                scaleY = scaleX
            }) {
                Text("1", color = Acid, fontFamily = FontFamily(Font(R.font.anton)), fontSize = markSize.sp, lineHeight = (markSize * 1.15f).sp)
                Text("THE WORLD\nIS WATCHING.", color = Paper, fontFamily = FontFamily(Font(R.font.anton)), fontSize = 34.sp, lineHeight = 38.sp, textAlign = TextAlign.Center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(64.dp).height(3.dp).background(Muted.copy(alpha = .2f))) {
                    Box(Modifier.fillMaxWidth(arrival.value).fillMaxHeight().background(Acid))
                }
                Spacer(Modifier.height(14.dp))
                Text("MAKE YOUR MOMENT.", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
