package com.oneglobal.billboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneglobal.billboard.OneViewModel
import com.oneglobal.billboard.data.AuctionRules
import com.oneglobal.billboard.model.ChallengePhase
import com.oneglobal.billboard.model.HallEntry
import com.oneglobal.billboard.model.MainTab
import com.oneglobal.billboard.model.MessageStatus
import com.oneglobal.billboard.model.OneMessage
import com.oneglobal.billboard.model.OneUiState
import com.oneglobal.billboard.model.Overlay
import com.oneglobal.billboard.model.PaletteKey
import com.oneglobal.billboard.model.ReignReceipt
import com.oneglobal.billboard.model.WorldState
import com.oneglobal.billboard.ui.theme.Acid
import com.oneglobal.billboard.ui.theme.Cobalt
import com.oneglobal.billboard.ui.theme.Ice
import com.oneglobal.billboard.ui.theme.Ink
import com.oneglobal.billboard.ui.theme.InkRaised
import com.oneglobal.billboard.ui.theme.Magenta
import com.oneglobal.billboard.ui.theme.Muted
import com.oneglobal.billboard.ui.theme.Orange
import com.oneglobal.billboard.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val mono = FontFamily.Monospace
private val display = FontFamily.SansSerif

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OneApp(
    viewModel: OneViewModel,
    revenueCatReady: Boolean,
    oneSignalReady: Boolean,
    onPurchaseCredits: (Int, (Boolean, String, Int) -> Unit) -> Unit,
    onRestorePurchases: ((Boolean, String) -> Unit) -> Unit,
    onRequestPush: ((Boolean) -> Unit) -> Unit,
    onShareReceipt: (ReignReceipt) -> Unit,
    onShareONE: () -> Unit,
    onIdentifyUser: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDeletionHelp: () -> Unit,
) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var showSplash by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2_050)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
        return
    }

    BackHandler(ui.overlay != Overlay.NONE) { viewModel.closeOverlay() }

    LaunchedEffect(ui.takeoverPulse) {
        if (ui.takeoverPulse > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(world.currentUserId, oneSignalReady, revenueCatReady) {
        if (world.currentUserId.isNotBlank()) onIdentifyUser(world.currentUserId)
    }
    LaunchedEffect(ui.revengeBanner) {
        if (ui.revengeBanner != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(ui.toast) {
        if (ui.toast != null) {
            delay(3_200)
            viewModel.dismissToast()
        }
    }

    if (!ui.onboardingComplete) {
        OnboardingScreen(onEnter = viewModel::completeOnboarding)
        return
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedContent(
            targetState = ui.tab,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = .992f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 1.01f))
            },
            label = "main-tab",
        ) { tab ->
            when (tab) {
                MainTab.LIVE -> LiveScreen(
                    world = world,
                    ui = ui,
                    onChallenge = viewModel::openChallenge,
                    onReport = viewModel::openReport,
                    onShareONE = onShareONE,
                    onReact = viewModel::react,
                    onEcho = viewModel::echoCurrentMessage,
                    onShareReign = {
                        ui.receipt?.let {
                            onShareReceipt(it.copy(appViews = world.appViews, webViews = world.webViews))
                        }
                    },
                )
                MainTab.LIBRARY -> LibraryScreen(
                    world = world,
                    selectedId = ui.selectedMessageId,
                    onCompose = viewModel::openCompose,
                    onDeploy = { id ->
                        viewModel.selectMessage(id)
                        viewModel.openChallenge()
                    },
                )
                MainTab.HALL -> HallScreen(world)
                MainTab.YOU -> YouScreen(
                    world = world,
                    ui = ui,
                    revenueCatReady = revenueCatReady,
                    oneSignalReady = oneSignalReady,
                    onVault = viewModel::openVault,
                    onEnablePush = { viewModel.enablePush(onRequestPush) },
                    onShareONE = onShareONE,
                    onEditHandle = viewModel::openHandleEditor,
                    onIdentityBackup = viewModel::openIdentityBackup,
                    onOpenPrivacy = onOpenPrivacy,
                    onFeedback = viewModel::openFeedback,
                    onUnblockAll = viewModel::unblockAll,
                    onDeleteAccount = viewModel::openDeleteAccount,
                    onHowItWorks = viewModel::openHowItWorks,
                )
            }
        }

        BottomNav(
            selected = ui.tab,
            onSelected = viewModel::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = ui.revengeBanner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            RevengeBanner(
                text = ui.revengeBanner.orEmpty(),
                onOpen = viewModel::openLastReceipt,
                onDismiss = viewModel::dismissRevenge,
            )
        }

        AnimatedVisibility(
            visible = ui.toast != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 18.dp, end = 18.dp),
        ) {
            ToastBar(ui.toast.orEmpty())
        }

        AnimatedContent(
            targetState = ui.overlay,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = .97f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(targetScale = 1.025f))
            },
            modifier = Modifier.fillMaxSize(),
            label = "overlay",
        ) { overlay ->
            when (overlay) {
                Overlay.NONE -> Unit
                Overlay.CHALLENGE -> ChallengeOverlay(
                    world = world,
                    ui = ui,
                    onClose = viewModel::closeOverlay,
                    onSelectMessage = viewModel::selectMessage,
                    onBegin = viewModel::beginChallenge,
                    onOpenVault = viewModel::openVault,
                    onInfo = viewModel::openHowItWorks,
                )
                Overlay.RECEIPT -> ReceiptOverlay(
                    receipt = ui.receipt,
                    world = world,
                    onClose = viewModel::closeOverlay,
                    onShare = { ui.receipt?.let(onShareReceipt) },
                )
                Overlay.HANDLE -> HandleOverlay(
                    world = world,
                    ui = ui,
                    onTextChanged = viewModel::updateHandleText,
                    onSubmit = viewModel::submitHandle,
                    onKeepAnonymous = viewModel::dismissHandleEditor,
                    onClose = viewModel::dismissHandleEditor,
                )
                Overlay.IDENTITY -> IdentityOverlay(
                    ui = ui,
                    onClose = viewModel::closeOverlay,
                    onCreateCode = viewModel::createRecoveryCode,
                    onHandleChanged = viewModel::updateRecoveryHandle,
                    onCodeChanged = viewModel::updateRecoveryCode,
                    onRecover = viewModel::recoverIdentity,
                    onReclaim = viewModel::reclaimUnclaimedHandle,
                )
                Overlay.COMPOSE -> ComposeOverlay(
                    ui = ui,
                    onTextChanged = viewModel::updateComposeText,
                    onSubmit = viewModel::submitMessage,
                    onClose = viewModel::closeOverlay,
                )
                Overlay.VAULT -> VaultOverlay(
                    world = world,
                    revenueCatReady = revenueCatReady,
                    onClose = viewModel::closeOverlay,
                    onPurchase = { amount ->
                        onPurchaseCredits(amount) { success, message, granted ->
                            if (success && granted > 0) viewModel.grantPurchasedCredits(granted)
                            if (!success) {
                                // The monetisation shell intentionally remains safe in demo mode.
                            }
                        }
                    },
                    onRestore = {
                        onRestorePurchases { _, message -> viewModel.showToast(message) }
                    },
                )
                Overlay.REPORT -> ReportOverlay(
                    owner = world.reign.owner.handle,
                    canBlock = world.reign.owner.id != world.currentUserId &&
                        world.reign.owner.id != "00000000-0000-0000-0000-000000000001" &&
                        !world.currentContentBlocked,
                    onClose = viewModel::closeOverlay,
                    onReport = viewModel::report,
                    onBlock = viewModel::blockCurrentOwner,
                )
                Overlay.FEEDBACK -> FeedbackOverlay(
                    ui = ui,
                    onClose = viewModel::closeOverlay,
                    onCategorySelected = viewModel::selectFeedbackCategory,
                    onTextChanged = viewModel::updateFeedbackText,
                    onSubmit = viewModel::submitFeedback,
                )
                Overlay.DELETE_ACCOUNT -> DeleteAccountOverlay(
                    deleting = ui.accountDeleting,
                    onClose = viewModel::closeOverlay,
                    onDelete = viewModel::deleteAccount,
                    onHelp = onOpenDeletionHelp,
                )
                Overlay.HOW_IT_WORKS -> HowItWorksOverlay(onClose = viewModel::closeOverlay)
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "one-splash")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3_800, easing = LinearEasing)), label = "orbit")
    val pulse by transition.animateFloat(.94f, 1.06f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(64) { index ->
                val x = ((index * 79) % 521) / 521f * size.width
                val y = ((index * 151) % 547) / 547f * size.height
                drawCircle(if (index % 9 == 0) Acid.copy(alpha = .22f) else Paper.copy(alpha = .045f), if (index % 9 == 0) 2.2f else 1f, Offset(x, y))
            }
            drawCircle(Acid.copy(alpha = .06f), size.minDimension * .34f)
        }
        Box(Modifier.size(246.dp).rotate(spin)) {
            Box(Modifier.size(246.dp).border(1.dp, Acid.copy(alpha = .22f), CircleShape))
            Box(Modifier.align(Alignment.TopCenter).offset(y = (-4).dp).size(9.dp).clip(CircleShape).background(Acid))
            Box(Modifier.align(Alignment.BottomCenter).offset(y = 4.dp).size(5.dp).clip(CircleShape).background(Ice))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(pulse)) {
            Text("1", color = Acid, fontWeight = FontWeight.Black, fontSize = 154.sp, lineHeight = 136.sp, letterSpacing = (-10).sp)
            Text("ONE", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 7.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CONNECTING TO THE WORLD", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(2.dp).background(Color.White.copy(alpha = .08f))) {
                Box(Modifier.fillMaxWidth(.72f).fillMaxHeight().background(Acid))
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onEnter: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage("01", "THE INTERNET\nHAS ONE SCREEN.", "One person owns it. Everyone watches. Anyone can take it.", "◉", Acid, "WATCH THE WORLD"),
        OnboardingPage("02", "DON’T POST.\nTAKE CONTROL.", "Choose one approved message, enter the live race and become the only voice on ONE.", "ϟ", Orange, "TAKE THE SCREEN"),
        OnboardingPage("03", "MAKE EVERY\nSECOND COUNT.", "Your reign, verified views and place in The Hall are recorded live for everyone.", "♛", Ice, "LEAVE A MARK"),
    )
    val item = pages[page]
    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(item.color, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            repeat(7) { ring ->
                drawCircle(item.color.copy(alpha = .035f), size.minDimension * (.18f + ring * .09f), Offset(size.width * .78f, size.height * .22f), style = Stroke(1f))
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ONE / FIRST ENTRY", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
                Text("${page + 1} / ${pages.size}", color = item.color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { index ->
                    Box(Modifier.weight(1f).height(3.dp).background(if (index <= page) item.color else Color.White.copy(alpha = .09f)))
                }
            }
            AnimatedContent(page, transitionSpec = { (fadeIn(tween(280)) + slideInVertically { it / 5 }) togetherWith (fadeOut(tween(180)) + slideOutVertically { -it / 5 }) }, label = "onboarding-page", modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(.45f))
                    Box(Modifier.size(132.dp).clip(CircleShape).background(item.color).shadow(28.dp, CircleShape, spotColor = item.color.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                        Text(item.icon, color = Ink, fontWeight = FontWeight.Black, fontSize = 54.sp)
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(item.eyebrow, color = item.color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.6.sp)
                    Text(item.title, color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 39.sp, letterSpacing = (-1.8).sp, modifier = Modifier.padding(top = 9.dp))
                    Text(item.body, color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 15.dp, end = 24.dp))
                    Spacer(Modifier.weight(.35f))
                }
            }
            Button(onClick = { if (page < pages.lastIndex) page++ else onEnter() }, modifier = Modifier.fillMaxWidth().height(62.dp), colors = ButtonDefaults.buttonColors(containerColor = item.color, contentColor = Ink), shape = RoundedCornerShape(6.dp)) {
                Text(if (page == pages.lastIndex) "ENTER THE LIVE WORLD  →" else "CONTINUE  →", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            if (page > 0) Text("BACK", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { page-- }.padding(13.dp))
            else Spacer(Modifier.height(34.dp))
        }
    }
}

private data class OnboardingPage(val number: String, val title: String, val body: String, val icon: String, val color: Color, val eyebrow: String)

@Composable
private fun OnboardingStep(index: String, icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(index, color = Muted, fontFamily = mono, fontSize = 7.sp)
        Text(icon, color = Acid, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(vertical = 3.dp))
        Text(label, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = .7.sp)
    }
}

@Composable
private fun LiveScreen(
    world: WorldState,
    ui: OneUiState,
    onChallenge: () -> Unit,
    onReport: () -> Unit,
    onShareONE: () -> Unit,
    onShareReign: () -> Unit,
    onReact: (String) -> Unit,
    onEcho: () -> Unit,
) {
    val accent = palette(world.reign.palette)
    val isOwner = world.reign.owner.id == world.currentUserId
    val now = System.currentTimeMillis()
    val protectedSeconds = ((world.reign.protectedUntilMillis - now + 999L) / 1_000L).toInt().coerceAtLeast(0)

    Box(Modifier.fillMaxSize()) {
        LiveField(accent = accent, modifier = Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 84.dp),
        ) {
            LiveHeader(world, accent, onShareONE, onReport)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OwnerMarkLarge(world.reign.owner.initials, accent)
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        if (world.reign.owner.verified) {
                            Spacer(Modifier.width(5.dp))
                            Text("◆", color = accent, fontSize = 10.sp)
                        }
                    }
                    Text(
                        "${world.reign.owner.city.uppercase()}, ${world.reign.owner.countryCode}",
                        color = Muted,
                        fontFamily = mono,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 17.dp).height(1.dp).background(Color.White.copy(alpha = .16f)))
            LiveMapMessageStage(
                message = world.reign.message.text,
                accent = accent,
                connected = world.connected,
                city = world.reign.owner.city,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            ViewLedger(world, accent, protectedSeconds)
            Spacer(Modifier.height(12.dp))
            AuctionCard(
                world = world,
                accent = accent,
                protectedSeconds = protectedSeconds,
                isOwner = isOwner,
                onPrimary = if (isOwner) onShareReign else onChallenge,
            )
            Spacer(Modifier.height(14.dp))
            CrowdControls(world, onReact, onEcho)
        }

        if (ui.challengePhase == ChallengePhase.WON && ui.overlay == Overlay.CHALLENGE) {
            TakeoverFlash(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun LiveHeader(world: WorldState, accent: Color, onShare: () -> Unit, onReport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text("LIVE WORLD STATE", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = .7.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAction("◎", onShare)
            Spacer(Modifier.width(7.dp))
            CircleAction("⋮", onReport)
        }
    }
}

@Composable
private fun LiveMapMessageStage(
    message: String,
    accent: Color,
    connected: Boolean,
    city: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(7.dp))) {
        WorldMapBackdrop(accent, Modifier.fillMaxSize())
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (connected) "GLOBAL SIGNAL // LIVE" else "GLOBAL SIGNAL // RECONNECTING", color = if (connected) accent else Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 7.sp, letterSpacing = .8.sp)
            Text(city.uppercase(), color = Paper.copy(alpha = .48f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp)
        }
        Box(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(bottom = 17.dp)) {
            MessageStage(message = message, accent = accent)
        }
    }
}

@Composable
private fun WorldMapBackdrop(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Deliberately a map illustration, not a fabricated location claim. The live
        // owner city and all metrics shown above/below come from the server state.
        val clusters = listOf(
            listOf(.08f to .30f, .14f to .23f, .23f to .20f, .31f to .27f, .28f to .38f, .18f to .43f, .10f to .40f),
            listOf(.37f to .30f, .46f to .22f, .58f to .24f, .65f to .34f, .60f to .44f, .48f to .40f, .39f to .48f),
            listOf(.68f to .58f, .79f to .52f, .91f to .58f, .94f to .69f, .83f to .76f, .73f to .69f),
            listOf(.28f to .61f, .38f to .56f, .43f to .66f, .40f to .80f, .31f to .78f),
        )
        repeat(27) { row ->
            repeat(46) { column ->
                val x = (column + .5f) / 46f
                val y = (row + .5f) / 27f
                val inCluster = clusters.any { points ->
                    points.any { (cx, cy) -> (x - cx) * (x - cx) * 1.7f + (y - cy) * (y - cy) < .0085f }
                }
                if (inCluster && (row * 13 + column * 7) % 3 != 0) {
                    drawCircle(accent.copy(alpha = .12f), 1.15.dp.toPx(), Offset(x * size.width, y * size.height))
                }
            }
        }
        drawLine(accent.copy(alpha = .22f), Offset(size.width * .15f, size.height * .36f), Offset(size.width * .78f, size.height * .62f), strokeWidth = 1.dp.toPx())
        drawCircle(accent.copy(alpha = .75f), 3.dp.toPx(), Offset(size.width * .78f, size.height * .62f))
    }
}

@Composable
private fun MessageStage(message: String, accent: Color) {
    val size = when {
        message.length <= 22 -> 53.sp
        message.length <= 42 -> 43.sp
        message.length <= 62 -> 34.sp
        message.length <= 90 -> 28.sp
        else -> 23.sp
    }
    Column {
        Text(
            message,
            color = Paper,
            fontFamily = display,
            fontWeight = FontWeight.Black,
            fontSize = size,
            lineHeight = size * .91f,
            letterSpacing = (-1.8).sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ViewLedger(world: WorldState, accent: Color, protectedSeconds: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = .2f))
            .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveMetric("◉", "WATCHING NOW", formatNumber(world.liveWatchers), "PEOPLE", accent, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(62.dp).background(Color.White.copy(alpha = .16f)))
        LiveMetric(
            "◷",
            if (protectedSeconds > 0) "PROTECTION" else "SCREEN STATUS",
            if (protectedSeconds > 0) formatDuration(protectedSeconds.toLong()) else "LIVE",
            if (protectedSeconds > 0) "MIN : SEC" else "OPEN TO TAKE",
            accent,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveMetric(icon: String, label: String, value: String, footer: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Text("$icon  $label", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 3.dp))
        Text(footer, color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp, letterSpacing = .8.sp)
    }
}

@Composable
private fun CrowdControls(
    world: WorldState,
    onReact: (String) -> Unit,
    onEcho: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE REACTIONS", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = .8.sp)
            Text("${formatNumber(world.reactions.size)} TOTAL", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReactionOrb("🔥", "FIRE") { onReact("FIRE") }
            ReactionOrb("👏", "RESPECT") { onReact("RESPECT") }
            ReactionOrb("💯", "100") { onReact("100") }
            ReactionOrb("👀", "WATCH") { onReact("WATCH") }
            ReactionOrb("🚀", "ECHO") { onEcho() }
        }
    }
}

@Composable
private fun ReactionOrb(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(InkRaised)
                .border(1.dp, Color.White.copy(alpha = .1f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 19.sp)
        }
        Text(label, color = Muted, fontFamily = mono, fontSize = 6.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AuctionCard(
    world: WorldState,
    accent: Color,
    protectedSeconds: Int,
    isOwner: Boolean,
    onPrimary: () -> Unit,
) {
    val cooldown = world.cooldownRemainingSeconds
    val canRevenge = cooldown > 0 && world.credits > 0
    val canTake = protectedSeconds == 0 && (cooldown == 0 || canRevenge)
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onPrimary,
            enabled = isOwner || canTake,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = Ink,
                disabledContainerColor = Color.White.copy(alpha = .08f),
                disabledContentColor = Muted,
            ),
        ) {
            Text(
                when {
                    isOwner -> "⚡  BROADCAST YOUR REIGN"
                    protectedSeconds > 0 -> "TAKEOVER LANDS IN ${protectedSeconds}s"
                    cooldown == 0 -> "⚡  TAKE THE SCREEN"
                    canRevenge -> "🎟  REVENGE NOW — 1 TICKET"
                    else -> "FREE STEAL RECHARGES IN ${cooldown}s"
                },
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = .4.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isOwner) "The world is watching your message." else "Challenge ${world.reign.owner.handle} to take ONE.",
            color = Muted,
            fontFamily = mono,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LibraryScreen(
    world: WorldState,
    selectedId: String?,
    onCompose: () -> Unit,
    onDeploy: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        SectionHeader("YOUR WORDS", "${world.messages.count { it.status == MessageStatus.APPROVED }} APPROVED")
        Spacer(Modifier.height(24.dp))
        Text("YOUR WORDS,\nREADY FOR THE WORLD.", color = Paper, fontWeight = FontWeight.Black, fontSize = 39.sp, lineHeight = 37.sp, letterSpacing = (-1.4).sp)
        Text("Messages are screened before they can enter the live battle.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 11.dp, bottom = 20.dp))
        PrimaryButton("+  COMPOSE A MESSAGE", Acid, onCompose)
        Spacer(Modifier.height(18.dp))
        world.messages.forEach { message ->
            MessageCard(
                message = message,
                selected = message.id == selectedId,
                onDeploy = { onDeploy(message.id) },
            )
            Spacer(Modifier.height(11.dp))
        }
        Spacer(Modifier.height(6.dp))
        SafetyCard()
    }
}

@Composable
private fun MessageCard(message: OneMessage, selected: Boolean, onDeploy: () -> Unit) {
    val statusColor = when (message.status) {
        MessageStatus.APPROVED -> Acid
        MessageStatus.REVIEWING -> Ice
        MessageStatus.REJECTED -> Orange
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(InkRaised)
            .border(1.dp, if (selected) statusColor.copy(alpha = .65f) else Color.White.copy(alpha = .08f), RoundedCornerShape(7.dp))
            .padding(17.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatusPill(message.status.name, statusColor)
            Text("USED ${message.timesDeployed}×", color = Muted, fontFamily = mono, fontSize = 7.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("“${message.text}”", color = if (message.status == MessageStatus.APPROVED) Paper else Muted, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 24.sp)
        if (message.status == MessageStatus.APPROVED) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier.clickable(onClick = onDeploy),
                    color = statusColor,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("DEPLOY  →", color = Ink, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun HallScreen(world: WorldState) {
    var period by rememberSaveable { mutableStateOf(HallPeriod.TODAY) }
    var showFull by rememberSaveable { mutableStateOf(false) }
    val today = world.hallToday.ifEmpty { world.hall }
    val allTime = world.hallAllTime.ifEmpty { world.hall }
    val selectedEntries = if (period == HallPeriod.TODAY) today else allTime
    val visibleEntries = if (showFull) selectedEntries else selectedEntries.take(4)
    val selectedPeriodIsLive = world.connected && !world.demoMode && (period == HallPeriod.TODAY || world.hallAllTimeLive)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("THE HALL.", color = Paper, fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 50.sp, letterSpacing = (-2.2).sp)
                Text("RANKED FROM VERIFIED LIVE REIGNS", color = Muted, fontFamily = mono, fontSize = 7.sp, letterSpacing = .8.sp, modifier = Modifier.padding(top = 7.dp))
            }
            StatusPill(if (selectedPeriodIsLive) "LIVE DATA" else if (world.demoMode) "PREVIEW" else "SYNCING", if (selectedPeriodIsLive) Acid else Orange)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(43.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(7.dp))) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (period == HallPeriod.TODAY) Acid else Color.Transparent).clickable { period = HallPeriod.TODAY; showFull = false }, contentAlignment = Alignment.Center) {
                Text("TODAY", color = if (period == HallPeriod.TODAY) Ink else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(if (period == HallPeriod.ALL_TIME) Acid else Color.Transparent).clickable { period = HallPeriod.ALL_TIME; showFull = false }, contentAlignment = Alignment.Center) {
                Text("ALL TIME", color = if (period == HallPeriod.ALL_TIME) Ink else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text("#", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.width(28.dp))
            Text("OWNER", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.weight(1f))
            Text("REIGN", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
            Text("VIEWS", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(7.dp))
        AnimatedContent(period, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "hall-period") {
            Column {
                if (visibleEntries.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("◇", color = Acid, fontSize = 30.sp)
                        Text("THE HALL IS WAITING", color = Paper, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                        Text("The first verified reign will appear here.", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                } else {
                    visibleEntries.forEach { entry -> HallRow(entry, entry.owner.id == world.currentUserId) }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(color = if (showFull) Acid else Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Acid.copy(alpha = .75f)), modifier = Modifier.fillMaxWidth().clickable { showFull = !showFull }) {
            Text(if (showFull) "COLLAPSE LEADERBOARD  ↑" else "VIEW FULL LEADERBOARD  ↓", color = if (showFull) Ink else Paper, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(16.dp))
        }
        Text("${selectedEntries.size} VERIFIED REIGNS // ${if (selectedPeriodIsLive) "UPDATED LIVE" else "SERVER UPDATE REQUIRED"}", color = Muted, fontFamily = mono, fontSize = 7.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
}

private enum class HallPeriod { TODAY, ALL_TIME }

@Composable
private fun YouScreen(
    world: WorldState,
    ui: OneUiState,
    revenueCatReady: Boolean,
    oneSignalReady: Boolean,
    onVault: () -> Unit,
    onEnablePush: () -> Unit,
    onShareONE: () -> Unit,
    onEditHandle: () -> Unit,
    onIdentityBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onFeedback: () -> Unit,
    onUnblockAll: () -> Unit,
    onDeleteAccount: () -> Unit,
    onHowItWorks: () -> Unit,
) {
    val user = world.currentUser
    val handle = user?.handle ?: if (world.demoMode) "@BOWEI" else "@CONNECTING"
    val initials = user?.initials ?: if (world.demoMode) "BW" else "--"
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("YOU.", color = Paper, fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 50.sp, letterSpacing = (-2.2).sp)
            CircleAction("ⓘ", onHowItWorks)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Ink).border(2.dp, Acid, CircleShape), contentAlignment = Alignment.Center) {
                Text(initials, color = Acid, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (world.connected) "${user?.city ?: "EARTH"}, ${user?.countryCode ?: "XX"}" else "CONNECTING", color = Muted, fontFamily = mono, fontSize = 9.sp, letterSpacing = .7.sp)
            }
            Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = .16f)))
            Column(Modifier.padding(start = 16.dp).clickable(onClick = onVault), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(world.credits.toString(), color = Acid, fontWeight = FontWeight.Black, fontSize = 25.sp)
                Text("TICKETS", color = Muted, fontFamily = mono, fontSize = 7.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("YOUR STATS", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = .8.sp)
        Spacer(Modifier.height(8.dp))
        UserStatsPanel(world)
        Spacer(Modifier.height(14.dp))
        SettingsCard(
            title = "PUBLIC HANDLE",
            detail = "$handle is your public alias. ONE never asks for your legal name.",
            badge = "EDIT",
            accent = Ice,
            onClick = onEditHandle,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "REVENGE ALERTS",
            detail = if (ui.pushEnabled) "Armed. You’ll know the second ONE is taken." else "Get the exact views from your reign when dethroned.",
            badge = when {
                ui.pushEnabled -> "ON"
                oneSignalReady -> "ARM"
                else -> "OFF"
            },
            accent = Orange,
            onClick = onEnablePush,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "IDENTITY BACKUP",
            detail = "Create a private recovery code so your handle and history can survive a lost session.",
            badge = "SAVE",
            accent = Acid,
            onClick = onIdentityBackup,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "REVENGE TICKETS",
            detail = if (revenueCatReady) "Live ticket packs are connected and server verified." else "Add the public SDK key to activate ticket packs.",
            badge = "›",
            accent = Acid,
            onClick = onVault,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "INVITE THE AUDIENCE",
            detail = "Share ONE. Grow the movement.",
            badge = "›",
            accent = Ice,
            onClick = onShareONE,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "PRIVACY & DATA",
            detail = "Control your data and visibility.",
            badge = "›",
            accent = Ice,
            onClick = onOpenPrivacy,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "SUPPORT",
            detail = "Help centre, feedback and contact options.",
            badge = "›",
            accent = Acid,
            onClick = onFeedback,
        )
        if (world.blockedCount > 0) {
            Spacer(Modifier.height(10.dp))
            SettingsCard(
                title = "BLOCKED ACCOUNTS",
                detail = "${world.blockedCount} account(s) hidden from your live screen, Hall and activity.",
                badge = "CLEAR",
                accent = Orange,
                onClick = onUnblockAll,
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "DELETE ACCOUNT",
            detail = "Permanently remove this anonymous identity, messages and activity.",
            badge = "DELETE",
            accent = Orange,
            onClick = onDeleteAccount,
        )
        Spacer(Modifier.height(18.dp))
        Text(if (world.connected) "GLOBAL LEDGER CONNECTED" else "GLOBAL LEDGER OFFLINE", color = if (world.connected) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.2.sp)
        Text("Ownership, cooldowns, tickets, views and race ordering are controlled by the server.", color = Muted, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ChallengeOverlay(
    world: WorldState,
    ui: OneUiState,
    onClose: () -> Unit,
    onSelectMessage: (String) -> Unit,
    onBegin: () -> Unit,
    onOpenVault: () -> Unit,
    onInfo: () -> Unit,
) {
    val accent = Acid
    val needsTicket = world.cooldownRemainingSeconds > 0
    val canAttempt = !needsTicket || world.credits > 0
    val selected = world.messages.firstOrNull { it.id == ui.selectedMessageId }
    val busy = ui.challengePhase !in listOf(ChallengePhase.IDLE, ChallengePhase.FAILED)

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).background(Orange).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onClose))
                Text("TAKE ONE", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("ⓘ", color = Ink, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onInfo).padding(8.dp))
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 17.dp),
            ) {
                Text("CURRENT OWNER", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = .8.sp)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnerMark(world.reign.owner.initials, accent)
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("${world.reign.owner.city.uppercase()}, ${world.reign.owner.countryCode}", color = Muted, fontFamily = mono, fontSize = 9.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 15.dp).height(1.dp).background(Color.White.copy(alpha = .13f)))
                Text("THE MESSAGE", color = Muted, fontFamily = mono, fontSize = 8.sp)
                Text(world.reign.message.text, color = Paper, fontWeight = FontWeight.Black, fontSize = 25.sp, lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text("Choose your move.", color = Muted, fontFamily = mono, fontSize = 9.sp, modifier = Modifier.padding(top = 9.dp, bottom = 14.dp))

                ChallengeChoice("ϟ", "FREE STEAL", "Fastest path. Ready after cooldown.", if (needsTicket) "${world.cooldownRemainingSeconds}s" else "READY", Acid, selected = !needsTicket)
                Spacer(Modifier.height(10.dp))
                ChallengeChoice("🎟", "REVENGE TICKET", "Skip the cooldown. Spent only if you win.", "${world.credits} LEFT", Ice, selected = needsTicket && world.credits > 0, onClick = if (world.credits == 0) onOpenVault else null)
                Spacer(Modifier.height(10.dp))
                ChallengeChoice("LIVE", "LIVE CHALLENGE", "Hold to submit an atomic server-verified takeover.", "ATOMIC", Orange, selected = false)
                Spacer(Modifier.height(16.dp))
                Text("YOUR APPROVED MESSAGE", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = .7.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    world.messages.filter { it.status == MessageStatus.APPROVED }.forEach { message ->
                        SelectableMessage(message, selected = message.id == ui.selectedMessageId) {
                            if (!busy) onSelectMessage(message.id)
                        }
                    }
                }
                Spacer(Modifier.height(17.dp))

                AnimatedContent(ui.challengePhase, label = "challenge-phase") { phase ->
                    when (phase) {
                        ChallengePhase.IDLE, ChallengePhase.FAILED -> Column {
                            if (phase == ChallengePhase.FAILED) {
                                ErrorStrip(ui.challengeStatus)
                                Spacer(Modifier.height(10.dp))
                            }
                            if (!canAttempt) {
                                PrimaryButton("GET A REVENGE TICKET", Ice, onOpenVault)
                            } else {
                                HoldToOwnButton(
                                    text = if (needsTicket) "HOLD TO CONTINUE — 1 TICKET" else "HOLD TO CONTINUE — FREE",
                                    enabled = selected != null,
                                    onComplete = onBegin,
                                )
                            }
                        }
                        ChallengePhase.WON -> WonPanel(selected?.text.orEmpty())
                        else -> ProtocolProgress(phase, ui.challengeStatus)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (ui.challengePhase == ChallengePhase.WON) TakeoverFlash(Modifier.fillMaxSize())
    }
}

@Composable
private fun UserStatsPanel(world: WorldState) {
    val longest = world.history.maxOfOrNull { it.durationSeconds } ?: world.userDailyReignSeconds
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(8.dp)).padding(vertical = 15.dp)) {
        ProfileStat("⬡", formatDuration(longest.toLong()), "LONGEST REIGN", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(58.dp).background(Color.White.copy(alpha = .14f)))
        ProfileStat("ϟ", world.userRetakesToday.toString(), "TAKEOVERS", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(58.dp).background(Color.White.copy(alpha = .14f)))
        ProfileStat("◉", formatNumber(world.history.sumOf { it.totalViews }), "VERIFIED VIEWS", Modifier.weight(1f))
    }
}

@Composable
private fun ProfileStat(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = Paper, fontSize = 14.sp)
        Text(value, color = Acid, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp))
        Text(label, color = Muted, fontFamily = mono, fontSize = 6.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun ChallengeChoice(
    icon: String,
    title: String,
    detail: String,
    status: String,
    color: Color,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) color else InkRaised)
            .border(1.dp, color.copy(alpha = if (selected) 1f else .58f), RoundedCornerShape(7.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = if (selected) Ink else color, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(detail, color = if (selected) Ink.copy(alpha = .68f) else Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(status, color = if (selected) Ink else color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun HowItWorksOverlay(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(Acid, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            OverlayHeader("THE RULES", "ONE SCREEN // ONE OWNER", onClose)
            Spacer(Modifier.height(30.dp))
            Text("SIMPLE ENOUGH\nTO FEEL DANGEROUS.", color = Paper, fontWeight = FontWeight.Black, fontSize = 39.sp, lineHeight = 36.sp, letterSpacing = (-1.5).sp)
            Text("Everything you tap in ONE changes the same live world for everyone.", color = Muted, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
            RuleCard("01", "◉", "WATCH", "There is only one live message. Views count once per verified viewer and reign.", Acid)
            Spacer(Modifier.height(10.dp))
            RuleCard("02", "ϟ", "TAKE", "Choose an approved message and hold to challenge. The server decides the winner atomically.", Orange)
            Spacer(Modifier.height(10.dp))
            RuleCard("03", "♛", "DEFEND", "Your reign lasts until somebody takes it. Every second and verified view enters The Hall.", Ice)
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Acid).padding(18.dp)) {
                Text("NO FAKE NUMBERS.", color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("LIVE DATA IS LABELLED LIVE. When disconnected, ONE says so instead of pretending.", color = Ink.copy(alpha = .68f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("I’M READY  →", Acid, onClose)
        }
    }
}

@Composable
private fun RuleCard(index: String, icon: String, title: String, detail: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(InkRaised).border(1.dp, color.copy(alpha = .42f), RoundedCornerShape(7.dp)).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) { Text(icon, color = Ink, fontWeight = FontWeight.Black, fontSize = 23.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("$index / $title", color = Paper, fontWeight = FontWeight.Black, fontSize = 16.sp)
            Text(detail, color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun IdentityOverlay(
    ui: OneUiState,
    onClose: () -> Unit,
    onCreateCode: () -> Unit,
    onHandleChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onRecover: () -> Unit,
    onReclaim: () -> Unit,
) {
    val canRecover = ui.recoveryHandle.length >= AuctionRules.HANDLE_MIN && ui.recoveryCodeInput.length >= 10
    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(Acid, Modifier.fillMaxSize().graphicsLayer { alpha = .24f })
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            OverlayHeader("IDENTITY", "RECOVERY // PRIVATE CODE", onClose)
            Spacer(Modifier.height(24.dp))
            StatusPill("DO NOT LOSE YOUR NAME", Acid)
            Spacer(Modifier.height(13.dp))
            Text("KEEP YOUR\nPLACE IN ONE.", color = Paper, fontWeight = FontWeight.Black, fontSize = 39.sp, lineHeight = 37.sp, letterSpacing = (-1.5).sp)
            Text("Your alias, Hall history and tickets are attached to this anonymous account. Create one recovery code and save it somewhere private.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 11.dp))
            Spacer(Modifier.height(20.dp))

            ui.recoveryCode?.let { code ->
                Surface(color = Acid.copy(alpha = .1f), shape = RoundedCornerShape(17.dp), border = BorderStroke(1.dp, Acid.copy(alpha = .52f))) {
                    Column(Modifier.fillMaxWidth().padding(17.dp)) {
                        Text("YOUR RECOVERY CODE", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
                        Text(code, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(top = 10.dp))
                        Text("Save it off this phone. Anyone with this code can recover this identity.", color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            } ?: Button(
                onClick = onCreateCode,
                enabled = !ui.identityBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (ui.identityBusy) "CREATING BACKUP..." else "CREATE MY RECOVERY CODE", fontWeight = FontWeight.Black, fontSize = 11.sp) }

            ui.identityError?.let { Spacer(Modifier.height(10.dp)); ErrorStrip(it) }
            Spacer(Modifier.height(26.dp))
            Text("LOST YOUR IDENTITY?", color = Ice, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
            Text("Use the handle and recovery code you saved to attach that identity to this installation.", color = Muted, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 7.dp, bottom = 11.dp))
            IdentityInput("OLD HANDLE", "B0WEI", ui.recoveryHandle, onHandleChanged, prefix = "@")
            Spacer(Modifier.height(10.dp))
            IdentityInput("RECOVERY CODE", "ONE-XXXX-XXXX-XXXX", ui.recoveryCodeInput, onCodeChanged)
            Spacer(Modifier.height(13.dp))
            Button(
                onClick = onRecover,
                enabled = canRecover && !ui.identityBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ice, contentColor = Ink, disabledContainerColor = Color.White.copy(alpha = .08f), disabledContentColor = Muted),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (ui.identityBusy) "VERIFYING..." else "RECOVER THIS IDENTITY", fontWeight = FontWeight.Black, fontSize = 10.sp) }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = ui.recoveryHandle.length >= AuctionRules.HANDLE_MIN && !ui.identityBusy, onClick = onReclaim),
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Orange.copy(alpha = .55f)),
            ) {
                Text("RECLAIM UNUSED GHOST ALIAS", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(15.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Reclaim only works for a two-week-old unused shell with no history, purchases or recovery code. Real identities always require their private recovery code.", color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun IdentityInput(label: String, placeholder: String, value: String, onChange: (String) -> Unit, prefix: String = "") {
    Column {
        Text(label, color = Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(13.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .18f), RoundedCornerShape(13.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (prefix.isNotEmpty()) Text(prefix, color = Acid, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, color = Muted.copy(alpha = .45f), fontFamily = mono, fontSize = 12.sp)
                BasicTextField(value = value, onValueChange = onChange, textStyle = TextStyle(color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp), singleLine = true, cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HandleOverlay(
    world: WorldState,
    ui: OneUiState,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onKeepAnonymous: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentAlias = world.currentUser?.handle ?: "@PLAYER"
    val candidate = AuctionRules.normalizeHandle(ui.handleText)
    val valid = ui.handleText.isNotBlank() && AuctionRules.validateHandle(candidate) == null
    val remaining = AuctionRules.HANDLE_MAX - candidate.length

    LaunchedEffect(Unit) {
        delay(260)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(Acid, Modifier.fillMaxSize().graphicsLayer { alpha = .32f })
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            OverlayHeader("IDENTITY", "NO SIGNUP // PUBLIC ALIAS", onClose)
            Spacer(Modifier.height(28.dp))

            StatusPill(if (ui.handleAfterFirstWin) "THE SCREEN IS YOURS" else "GLOBAL IDENTITY", Acid)
            Spacer(Modifier.height(14.dp))
            Text(
                if (ui.handleAfterFirstWin) "YOU'RE LIVE.\nWHO TOOK IT?" else "WHAT SHOULD\nTHE WORLD CALL YOU?",
                color = Paper,
                fontWeight = FontWeight.Black,
                fontSize = 45.sp,
                lineHeight = 42.sp,
                letterSpacing = (-1.7).sp,
            )
            Text(
                if (ui.handleAfterFirstWin) "Your words are on the only screen. Give everyone a name to chase."
                else "Choose a memorable alias for takeovers, rivalries and the Hall.",
                color = Muted,
                fontFamily = mono,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )

            ui.receipt?.message?.takeIf { ui.handleAfterFirstWin }?.let { message ->
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = Acid.copy(alpha = .09f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Acid.copy(alpha = .28f)),
                ) {
                    Text("\"$message\"", color = Acid, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth().padding(15.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("YOUR HANDLE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(InkRaised)
                    .border(1.dp, if (ui.handleError == null) Acid.copy(alpha = .52f) else Orange, RoundedCornerShape(21.dp))
                    .padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@", color = Acid, fontWeight = FontWeight.Black, fontSize = 29.sp)
                Box(Modifier.weight(1f).padding(start = 2.dp)) {
                    if (ui.handleText.isEmpty()) {
                        Text("BOWEI", color = Muted.copy(alpha = .45f), fontWeight = FontWeight.Black, fontSize = 29.sp)
                    }
                    BasicTextField(
                        value = ui.handleText,
                        onValueChange = onTextChanged,
                        textStyle = TextStyle(color = Paper, fontWeight = FontWeight.Black, fontSize = 29.sp),
                        singleLine = true,
                        cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                Text(remaining.toString(), color = if (remaining < 0) Orange else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }

            ui.handleError?.let {
                Spacer(Modifier.height(10.dp))
                ErrorStrip(it)
            }

            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Ice.copy(alpha = .07f))
                    .border(1.dp, Ice.copy(alpha = .2f), RoundedCornerShape(17.dp))
                    .padding(15.dp),
            ) {
                Text("HANDLE ONLY. NEVER YOUR LEGAL NAME.", color = Ice, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
                Text("No name or email is public. You still have a private account ID and standard server logs, and abuse gets banned.", color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSubmit,
                enabled = valid && !ui.handleSaving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Acid,
                    contentColor = Ink,
                    disabledContainerColor = Color.White.copy(alpha = .08f),
                    disabledContentColor = Muted,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    when {
                        ui.handleSaving -> "CLAIMING @$candidate..."
                        valid -> "MAKE @$candidate LIVE  ->"
                        else -> "CHOOSE YOUR HANDLE"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !ui.handleSaving, onClick = onKeepAnonymous),
                color = Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
            ) {
                Text(
                    if (ui.handleAfterFirstWin) "NOT NOW - KEEP $currentAlias" else "CANCEL - KEEP $currentAlias",
                    color = Muted,
                    fontFamily = mono,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(15.dp))
            Text("3-18 CHARACTERS  //  LETTERS, NUMBERS, UNDERSCORES", color = Muted, fontFamily = mono, fontSize = 7.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReceiptOverlay(
    receipt: ReignReceipt?,
    world: WorldState,
    onClose: () -> Unit,
    onShare: () -> Unit,
) {
    val resolved = receipt ?: return
    val stillOwner = world.reign.owner.id == resolved.owner.id
    val appViews = if (stillOwner) world.appViews else resolved.appViews
    val webViews = if (stillOwner) world.webViews else resolved.webViews
    val total = appViews + webViews
    val duration = if (stillOwner) ((System.currentTimeMillis() - resolved.startedAtMillis) / 1_000L).toInt() else resolved.durationSeconds

    Box(Modifier.fillMaxSize().background(Ink)) {
        VictoryConfetti(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OneLogo(Acid, compact = true)
                Text(if (stillOwner) "LIVE OWNERSHIP" else "REIGN COMPLETE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
                CircleAction("×", onClose)
            }
            Spacer(Modifier.height(20.dp))
            VictorySeal()
            Spacer(Modifier.height(18.dp))
            Text(if (stillOwner) "YOU OWN ONE." else "YOUR REIGN\nIS HISTORY.", color = Paper, fontWeight = FontWeight.Black, fontSize = 46.sp, lineHeight = 43.sp, letterSpacing = (-2.sp))
            Text(if (stillOwner) "You took the screen. It’s yours." else "The screen moved on. Your proof remains.", color = Muted, fontFamily = mono, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            Text("YOUR LIVE MESSAGE", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = .8.sp)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(InkRaised).border(1.dp, Acid.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(17.dp)) {
                Text(resolved.message, color = Paper, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 23.sp)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .34f)).border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(8.dp)).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ReceiptMetric(if (stillOwner) "04:59" else formatDuration(duration.toLong()), if (stillOwner) "PROTECTION" else "REIGN", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(48.dp).background(Color.White.copy(alpha = .14f)))
                ReceiptMetric(formatNumber(total), "VERIFIED VIEWS", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (stillOwner) "GO LIVE  ((•))" else "WATCH LIVE", Acid, onClose)
            Spacer(Modifier.height(10.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onShare), color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
                Text("SHARE THE PROOF  ↗", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(15.dp))
            }
        }
    }
}

@Composable
private fun VictorySeal() {
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(164.dp).border(2.dp, Acid, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(116.dp).border(1.dp, Acid.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Text("1", color = Acid, fontWeight = FontWeight.Black, fontSize = 78.sp, letterSpacing = (-5).sp)
            }
            Text("YOU OWN ONE", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            Text("THE WORLD IS WATCHING", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 6.sp, letterSpacing = .7.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun VictoryConfetti(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val colors = listOf(Acid, Orange, Ice)
        repeat(24) { index ->
            val x = ((index * 97) % 389) / 389f * size.width
            val y = ((index * 53) % 173) / 173f * size.height * .32f
            drawRect(colors[index % colors.size].copy(alpha = .8f), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 10.dp.toPx()))
        }
    }
}

@Composable
private fun ComposeOverlay(
    ui: OneUiState,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val remaining = AuctionRules.MESSAGE_LIMIT - ui.composeText.trim().length
    val valid = ui.composeText.isNotBlank() && ui.composeError == null && remaining >= 0
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("COMPOSE", "PRE-CLEAR BEFORE BIDDING", onClose)
        Spacer(Modifier.height(26.dp))
        Text("SAY ONE THING\nWORTH STEALING.", color = Paper, fontWeight = FontWeight.Black, fontSize = 42.sp, lineHeight = 40.sp, letterSpacing = (-1.5).sp)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF151A08), Color(0xFF090A06))))
                .border(1.dp, Acid.copy(alpha = .4f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            if (ui.composeText.isEmpty()) {
                Text("THE WHOLE WORLD WILL SEE…", color = Muted.copy(alpha = .55f), fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 30.sp)
            }
            BasicTextField(
                value = ui.composeText,
                onValueChange = onTextChanged,
                textStyle = TextStyle(color = Paper, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 30.sp),
                modifier = Modifier.fillMaxSize(),
                cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)),
            )
            Text("$remaining", color = if (remaining < 0) Orange else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        ui.composeError?.let {
            Spacer(Modifier.height(10.dp))
            ErrorStrip(it)
        }
        Spacer(Modifier.height(17.dp))
        ScreeningRow("ON-DEVICE PRE-FILTER", ui.composeText.isNotBlank())
        ScreeningRow("NO LINKS OR PHONE NUMBERS", ui.composeError?.contains("Links") != true && ui.composeError?.contains("Phone") != true)
        ScreeningRow("SERVER SAFETY REVIEW", valid)
        ScreeningRow("REPORT + BAN CONTROLS", true)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink, disabledContainerColor = Color.White.copy(alpha = .08f), disabledContentColor = Muted),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("SUBMIT FOR APPROVAL  →", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Approval happens before the live battle. A takeover never waits for moderation.", color = Muted, fontFamily = mono, textAlign = TextAlign.Center, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VaultOverlay(
    world: WorldState,
    revenueCatReady: Boolean,
    onClose: () -> Unit,
    onPurchase: (Int) -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("‹", if (revenueCatReady) "LIVE STORE" else "STORE OFFLINE", onClose)
        Spacer(Modifier.height(24.dp))
        Text("REVENGE TICKETS.", color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 40.sp, letterSpacing = (-1.8).sp)
        Text("Skip your cooldown. Take ONE back now.", color = Muted, fontFamily = mono, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp).height(1.dp).background(Color.White.copy(alpha = .14f)))
        Text("YOUR BALANCE", color = Muted, fontFamily = mono, fontSize = 8.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Text("🎟", fontSize = 27.sp)
            Spacer(Modifier.width(12.dp))
            Text("${formatNumber(world.credits)} TICKETS", color = Acid, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        CreditPack("ϟ", "SPARK", 3, "£0.99", Acid) { onPurchase(3) }
        Spacer(Modifier.height(11.dp))
        CreditPack("✦", "CHALLENGER", 20, "£4.99", Ice) { onPurchase(20) }
        Spacer(Modifier.height(11.dp))
        CreditPack("♛", "HEADLINER", 50, "£9.99", Orange) { onPurchase(50) }
        Spacer(Modifier.height(22.dp))
        Text("PURCHASES ARE PROCESSED BY GOOGLE PLAY AND VERIFIED SERVER-SIDE BEFORE TICKETS ARE ADDED.", color = Muted, fontFamily = mono, fontSize = 7.sp, lineHeight = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Surface(modifier = Modifier.fillMaxWidth().clickable(enabled = revenueCatReady, onClick = onRestore), color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .16f))) {
            Text("↻  RESTORE GOOGLE PLAY PURCHASES", color = if (revenueCatReady) Paper else Muted, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun ReportOverlay(
    owner: String,
    canBlock: Boolean,
    onClose: () -> Unit,
    onReport: (String) -> Unit,
    onBlock: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        OverlayHeader("SAFETY", "PUBLIC UGC CONTROL", onClose)
        Spacer(Modifier.height(30.dp))
        Text("REPORT THE\nLIVE MESSAGE.", color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 40.sp)
        Text("Reports enter the global moderation queue. Blocking immediately hides this person and their future content from you.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp, bottom = 22.dp))
        listOf("HATE OR HARASSMENT", "THREAT OR VIOLENCE", "PERSONAL INFORMATION", "SCAM OR IMPERSONATION", "OTHER").forEach { reason ->
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onReport(reason) }, color = InkRaised, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(reason, color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    Text("→", color = Orange, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (canBlock) {
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onBlock), color = Orange.copy(alpha = .12f), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .4f))) {
                Text("BLOCK $owner", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Safety contact: oneglobalscreen@gmail.com", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun FeedbackOverlay(
    ui: OneUiState,
    onClose: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val categories = listOf("BUG", "IDEA", "GAMEPLAY", "OTHER")
    val remaining = 1_000 - ui.feedbackText.length
    val canSubmit = ui.feedbackText.trim().length >= 3 && !ui.feedbackSending
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("FEEDBACK", "REAL TESTER NOTES", onClose)
        Spacer(Modifier.height(28.dp))
        Text("MAKE ONE\nBETTER.", color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 40.sp, letterSpacing = (-1.6).sp)
        Text("Your note is attached to your anonymous ONE account so we can investigate without collecting your name or email.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(22.dp))
        Text("WHAT TYPE OF NOTE?", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                val selected = ui.feedbackCategory == category
                Surface(
                    modifier = Modifier.clickable { onCategorySelected(category) },
                    color = if (selected) Acid else InkRaised,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (selected) Acid else Color.White.copy(alpha = .1f)),
                ) {
                    Text(category, color = if (selected) Ink else Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF151A08), Color(0xFF090A06))))
                .border(1.dp, Acid.copy(alpha = .4f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            if (ui.feedbackText.isEmpty()) {
                Text("WHAT HAPPENED?\nWHAT SHOULD CHANGE?", color = Muted.copy(alpha = .55f), fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp)
            }
            BasicTextField(
                value = ui.feedbackText,
                onValueChange = onTextChanged,
                textStyle = TextStyle(color = Paper, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 23.sp),
                modifier = Modifier.fillMaxSize(),
                cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)),
            )
            Text("$remaining", color = if (remaining < 0) Orange else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink, disabledContainerColor = Color.White.copy(alpha = .08f), disabledContentColor = Muted),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(if (ui.feedbackSending) "SENDING..." else "SEND TO THE ONE TEAM  ->", fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Please do not include passwords, payment details, or private information.", color = Muted, fontFamily = mono, textAlign = TextAlign.Center, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeleteAccountOverlay(
    deleting: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onHelp: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("PRIVACY", "IRREVERSIBLE ACTION", onClose)
        Spacer(Modifier.height(30.dp))
        Text("DELETE YOUR\nONE IDENTITY.", color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 40.sp)
        Text(
            "This permanently deletes your anonymous account, handle, message library, reactions, reports, tickets and device session. Past reigns remain only as anonymised @DELETED ledger entries so the global record cannot be rewritten.",
            color = Muted,
            fontFamily = mono,
            fontSize = 10.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(24.dp))
        Surface(color = Orange.copy(alpha = .1f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .38f))) {
            Column(Modifier.padding(18.dp)) {
                Text("NO UNDO. NO RECOVERY.", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text("ONE will create a completely new anonymous identity after deletion so the app can reopen safely.", color = Paper, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDelete,
            enabled = !deleting,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Ink, disabledContainerColor = Orange.copy(alpha = .35f)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (deleting) "DELETING SECURELY..." else "PERMANENTLY DELETE ACCOUNT", fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp), color = InkRaised, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
            Text("OPEN DELETION HELP", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 9.sp, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Questions: oneglobalscreen@gmail.com", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun BottomNav(selected: MainTab, onSelected: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(72.dp)
            .background(Color(0xFA080808))
            .border(1.dp, Color.White.copy(alpha = .12f), RectangleShape),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("ϟ", "LIVE", MainTab.LIVE, selected, onSelected)
        NavItem("♜", "CHALLENGES", MainTab.LIBRARY, selected, onSelected)
        NavItem("♛", "HALL", MainTab.HALL, selected, onSelected)
        NavItem("●", "YOU", MainTab.YOU, selected, onSelected)
    }
}

@Composable
private fun RowScope.NavItem(icon: String, label: String, tab: MainTab, selected: MainTab, onSelected: (MainTab) -> Unit) {
    val active = tab == selected
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onSelected(tab) }
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, color = if (active) Acid else Muted, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = if (active) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = if (label.length > 8) 6.sp else 7.sp, letterSpacing = .3.sp)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(34.dp).height(2.dp).background(if (active) Acid else Color.Transparent))
    }
}

@Composable
private fun LiveField(accent: Color, modifier: Modifier = Modifier) {
    // A device-sized radial shader made first render catastrophically slow on some
    // Android GPUs. Keep the field intentionally minimal: ONE's typography and live
    // state carry the visual drama, while the background stays cheap and responsive.
    Box(modifier.background(Ink).background(accent.copy(alpha = .025f))) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * .58f
            repeat(7) { index ->
                val y = horizon + index * 42.dp.toPx()
                drawLine(Color.White.copy(alpha = .025f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            repeat(48) { index ->
                val x = ((index * 83) % 431) / 431f * size.width
                val y = ((index * 149) % 467) / 467f * size.height
                drawCircle(Color.White.copy(alpha = .035f), 1f, Offset(x, y))
            }
        }
    }
}

@Composable
private fun CertificateField(modifier: Modifier = Modifier) {
    Canvas(modifier.graphicsLayer { alpha = .16f }) {
        val spacing = 22.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Ink, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            x += spacing
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += spacing
        }
    }
}

@Composable
private fun TakeoverFlash(modifier: Modifier = Modifier) {
    val scale = remember { Animatable(.1f) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1.15f, tween(700, easing = FastOutSlowInEasing))
        delay(520)
        alpha.animateTo(0f, tween(480))
    }
    Box(modifier.background(Acid.copy(alpha = alpha.value)), contentAlignment = Alignment.Center) {
        Text("ONE", color = Ink.copy(alpha = alpha.value), fontWeight = FontWeight.Black, fontSize = 96.sp, letterSpacing = (-6).sp, modifier = Modifier.scale(scale.value))
    }
}

@Composable
private fun HoldToOwnButton(text: String, enabled: Boolean, onComplete: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(1_250, easing = LinearEasing) else tween(180),
        label = "hold-progress",
    )
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(1_260)
            if (pressed) {
                pressed = false
                onComplete()
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (enabled) Color(0xFF24242A) else Color(0xFF151518))
            .border(1.dp, if (enabled) Acid.copy(alpha = .65f) else Muted.copy(alpha = .2f), RoundedCornerShape(5.dp))
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().align(Alignment.CenterStart).background(Acid))
        Text(text, color = if (progress > .55f) Ink else if (enabled) Paper else Muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .4.sp)
    }
}

@Composable
private fun ProtocolProgress(phase: ChallengePhase, status: String) {
    val steps = listOf(ChallengePhase.RESERVING, ChallengePhase.VERIFYING, ChallengePhase.SPENDING, ChallengePhase.COMMITTING)
    val current = steps.indexOf(phase).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .45f)).border(1.dp, Acid.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(status, color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            steps.forEachIndexed { index, _ ->
                Box(Modifier.weight(1f).height(7.dp).background(if (index <= current) Acid else Color.White.copy(alpha = .08f), CircleShape))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("IDEMPOTENCY KEY  ${System.currentTimeMillis().toString().takeLast(8)}", color = Muted, fontFamily = mono, fontSize = 7.sp)
    }
}

@Composable
private fun WonPanel(message: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Acid).padding(19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YOU TOOK ONE", color = Ink, fontWeight = FontWeight.Black, fontSize = 27.sp)
        Text("“$message”", color = Ink.copy(alpha = .72f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun RaceCard(world: WorldState) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(InkRaised).border(1.dp, Orange.copy(alpha = .5f), RoundedCornerShape(6.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        OwnerMark(world.reign.owner.initials, palette(world.reign.palette))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("CURRENT OWNER", color = Muted, fontFamily = mono, fontSize = 7.sp)
        }
        Text("→", color = Muted, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("YOU", color = Acid, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(if (world.cooldownRemainingSeconds > 0) "REVENGE" else "FREE STEAL", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }
    }
}

@Composable
private fun SelectableMessage(message: OneMessage, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(238.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Acid else InkRaised)
            .border(1.dp, if (selected) Acid else Color.White.copy(alpha = .09f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("“${message.text}”", color = if (selected) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(if (selected) "SELECTED  ◆" else "APPROVED", color = if (selected) Ink.copy(alpha = .6f) else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp)
    }
}

@Composable
private fun HallHero(entry: HallEntry) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(Acid, Color(0xFFB9E800)))).padding(20.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#01 // TODAY", color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp)
                Text(formatDuration(entry.reignSeconds.toLong()), color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Spacer(Modifier.height(26.dp))
            Text("“${entry.message}”", color = Ink, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 29.sp)
            Spacer(Modifier.height(23.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.owner.handle, color = Ink, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("${formatNumber(entry.verifiedViews)} VIEWS", color = Ink.copy(alpha = .7f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
        }
    }
}

@Composable
private fun HallRow(entry: HallEntry, isCurrentUser: Boolean = false) {
    val rankColor = when (entry.rank) { 1 -> Acid; 2 -> Ice; 3 -> Orange; else -> Muted }
    Row(Modifier.fillMaxWidth().height(70.dp).background(if (isCurrentUser) Acid else Color.Transparent).border(0.5.dp, Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(entry.rank.toString(), color = if (isCurrentUser) Ink else rankColor, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (isCurrentUser) Ink else rankColor.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(entry.owner.initials, color = if (isCurrentUser) Acid else rankColor, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.owner.handle, color = if (isCurrentUser) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 11.sp, maxLines = 1)
            Text("${entry.owner.city.uppercase()}, ${entry.owner.countryCode}", color = if (isCurrentUser) Ink.copy(alpha = .65f) else Muted, fontFamily = mono, fontSize = 6.sp)
        }
        Text(formatDuration(entry.reignSeconds.toLong()), color = if (isCurrentUser) Ink else Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
        Text(formatNumber(entry.verifiedViews), color = if (isCurrentUser) Ink else Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.width(62.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun WalletHero(credits: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(Color(0xFF27272E), Color(0xFF111115)))).border(1.dp, Acid.copy(alpha = .42f), RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(19.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ONE VAULT", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = 1.1.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(credits), color = Paper, fontWeight = FontWeight.Black, fontSize = 39.sp, letterSpacing = (-1).sp)
                    Text(" TICKETS", color = Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Box(Modifier.size(50.dp).clip(CircleShape).background(Acid), contentAlignment = Alignment.Center) {
                Text("+", color = Ink, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun DailyCapCard(world: WorldState) {
    val progress = (1f - world.cooldownRemainingSeconds / 30f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(7.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = .08f), style = Stroke(5.dp.toPx()))
                drawArc(Acid, -90f, progress.coerceIn(0f, 1f) * 360f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(if (world.cooldownRemainingSeconds == 0) "GO" else "${world.cooldownRemainingSeconds}s", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("FREE STEAL CHARGE", color = Paper, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(if (world.cooldownRemainingSeconds == 0) "Ready now. Your next takeover costs nothing." else "Wait or spend one Revenge Ticket to move instantly.", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, detail: String, badge: String, accent: Color, onClick: () -> Unit) {
    val icon = when {
        title.contains("ALERT") || title.contains("NOTIFICATION") -> "♢"
        title.contains("TICKET") -> "ϟ"
        title.contains("PRIVACY") -> "⬡"
        title.contains("INVITE") -> "◎"
        title.contains("SUPPORT") || title.contains("FEEDBACK") -> "?"
        title.contains("DELETE") -> "×"
        else -> "●"
    }
    Row(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(7.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, accent.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(detail, color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(badge, color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = if (badge == "›") 22.sp else 8.sp)
    }
}

@Composable
private fun CreditPack(icon: String, name: String, amount: Int, price: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = Ink, fontWeight = FontWeight.Black, fontSize = 34.sp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("${formatNumber(amount)} TICKETS", color = Ink.copy(alpha = .76f), fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.width(1.dp).height(72.dp).background(Ink.copy(alpha = .25f)))
        Text(price, color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun RuleStrip(vararg rules: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        rules.forEach { rule -> Text("• ${rule.uppercase()}", color = Muted, fontFamily = mono, fontSize = 6.sp) }
    }
}

@Composable
private fun SafetyCard() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Orange.copy(alpha = .08f)).border(1.dp, Orange.copy(alpha = .23f), RoundedCornerShape(18.dp)).padding(15.dp)) {
        Text("SAFETY IS PART OF THE PRODUCT", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
        Text("On-device pre-filter + server review + reporting + account bans + emergency global removal.", color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ScreeningRow(text: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, color = if (passed) Paper else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
        Text(if (passed) "PASS  ◆" else "WAIT", color = if (passed) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.height(112.dp).clip(RoundedCornerShape(19.dp)).background(InkRaised).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontFamily = mono, fontSize = 7.sp)
        Text(value, color = Paper, fontWeight = FontWeight.Black, fontSize = 29.sp)
    }
}

@Composable
private fun ErrorStrip(text: String) {
    Surface(color = Orange.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .35f))) {
        Text(text, color = Orange, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(11.dp))
    }
}

@Composable
private fun RevengeBanner(text: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Orange)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("⚡  DETHRONED", color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
            CircleActionDark("×", onDismiss)
        }

        Spacer(Modifier.height(34.dp))
        Text(
            "THEY\nTOOK\nONE.",
            color = Ink,
            fontWeight = FontWeight.Black,
            fontSize = 60.sp,
            lineHeight = 51.sp,
            letterSpacing = (-3).sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text.uppercase(),
            color = Ink,
            fontFamily = mono,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            lineHeight = 15.sp,
        )

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ReceiptMetric("STATUS", "STOLEN", Modifier.weight(1f), dark = true)
            ReceiptMetric("MOVE", "REVENGE", Modifier.weight(1f), dark = true)
        }
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(62.dp),
        ) {
            Text("⚡  TAKE IT BACK", fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Text(
            "OR CLOSE TO WATCH THE NEW OWNER",
            color = Ink.copy(alpha = .68f),
            fontFamily = mono,
            fontWeight = FontWeight.Black,
            fontSize = 7.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

@Composable
private fun ToastBar(text: String) {
    Surface(color = Paper, shape = RoundedCornerShape(14.dp), shadowElevation = 10.dp) {
        Text(text, color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
    }
}

@Composable
private fun SectionHeader(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Acid))
            Spacer(Modifier.width(7.dp))
            Text(left, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Text(right, color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
    }
}

@Composable
private fun OverlayHeader(left: String, right: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(left, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(right, color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.padding(top = 2.dp))
        }
        CircleAction("×", onClose)
    }
}

@Composable
private fun OneLogo(color: Color, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("1", color = color, fontWeight = FontWeight.Black, fontSize = if (compact) 25.sp else 34.sp, letterSpacing = (-1).sp)
        if (!compact) Text("ONE", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
private fun OwnerMark(initials: String, accent: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .15f)).border(1.dp, accent.copy(alpha = .65f), CircleShape), contentAlignment = Alignment.Center) {
        Text(initials, color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp)
    }
}

@Composable
private fun OwnerMarkLarge(initials: String, accent: Color) {
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Ink)
            .border(2.dp, accent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = accent, fontWeight = FontWeight.Black, fontSize = 19.sp)
    }
}

@Composable
private fun CircleAction(text: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .08f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Paper, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun CircleActionDark(text: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Ink.copy(alpha = .1f)).border(1.dp, Ink.copy(alpha = .25f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Ink, fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
private fun LedgerMetric(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(formatNumber(value), color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp)
        Text(label, color = Muted, fontFamily = mono, fontSize = 7.sp)
    }
}

@Composable
private fun ReceiptMetric(value: String, label: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (dark) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(label, color = if (dark) Ink.copy(alpha = .62f) else Muted, fontFamily = mono, fontSize = 6.sp)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, color.copy(alpha = .35f))) {
        Text(text, color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 7.sp, letterSpacing = .6.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color, onClick: () -> Unit, foreground: Color = Ink) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = foreground), shape = RoundedCornerShape(6.dp)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = .3.sp)
    }
}

@Composable
private fun DecaySparkline(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * .15f)
            cubicTo(size.width * .28f, size.height * .18f, size.width * .36f, size.height * .64f, size.width, size.height * .86f)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.dp.toPx(), Offset(size.width, size.height * .86f))
    }
}

private fun palette(key: PaletteKey): Color = when (key) {
    PaletteKey.ACID -> Acid
    PaletteKey.COBALT -> Cobalt
    PaletteKey.ORANGE -> Orange
    PaletteKey.MAGENTA -> Magenta
    PaletteKey.ICE -> Ice
}

private fun formatNumber(value: Int): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f)
    value >= 100_000 -> String.format("%.0fK", value / 1_000f)
    value >= 10_000 -> String.format("%.1fK", value / 1_000f)
    else -> String.format("%,d", value)
}

private fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        String.format("%02d:%02d:%02d", hours, minutes % 60, remainder)
    } else {
        String.format("%02d:%02d", minutes, remainder)
    }
}
