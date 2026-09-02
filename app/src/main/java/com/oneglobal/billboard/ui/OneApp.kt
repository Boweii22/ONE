package com.oneglobal.billboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
    onRequestPush: () -> Unit,
    onShareReceipt: (ReignReceipt) -> Unit,
    onShareONE: () -> Unit,
    onIdentifyUser: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDeletionHelp: () -> Unit,
) {
    val world by viewModel.world.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

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
                    onOpenPrivacy = onOpenPrivacy,
                    onUnblockAll = viewModel::unblockAll,
                    onDeleteAccount = viewModel::openDeleteAccount,
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
                Overlay.COMPOSE -> ComposeOverlay(
                    ui = ui,
                    onTextChanged = viewModel::updateComposeText,
                    onSubmit = viewModel::submitMessage,
                    onClose = viewModel::closeOverlay,
                )
                Overlay.VAULT -> VaultOverlay(
                    world = world,
                    ui = ui,
                    revenueCatReady = revenueCatReady,
                    onClose = viewModel::closeOverlay,
                    onWatchAd = viewModel::watchRewardedAd,
                    onPurchase = { amount ->
                        onPurchaseCredits(amount) { success, message, granted ->
                            if (success && granted > 0) viewModel.grantPurchasedCredits(granted)
                            if (!success) {
                                // The monetisation shell intentionally remains safe in demo mode.
                            }
                        }
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
                Overlay.DELETE_ACCOUNT -> DeleteAccountOverlay(
                    deleting = ui.accountDeleting,
                    onClose = viewModel::closeOverlay,
                    onDelete = viewModel::deleteAccount,
                    onHelp = onOpenDeletionHelp,
                )
            }
        }
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
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
        ) {
            LiveHeader(world, accent, onShareONE, onReport)
            Spacer(Modifier.weight(.24f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OwnerMark(world.reign.owner.initials, accent)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        if (world.reign.owner.verified) {
                            Spacer(Modifier.width(5.dp))
                            Text("◆", color = accent, fontSize = 9.sp)
                        }
                    }
                    Text(
                        "OWNS ONE // ${world.reign.owner.city}, ${world.reign.owner.countryCode}",
                        color = Muted,
                        fontFamily = mono,
                        fontSize = 8.sp,
                        letterSpacing = .9.sp,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            MessageStage(message = world.reign.message.text, accent = accent)
            Spacer(Modifier.height(22.dp))
            ViewLedger(world, accent)
            Spacer(Modifier.height(11.dp))
            CrowdControls(world, onReact, onEcho)
            Spacer(Modifier.weight(.34f))
            AuctionCard(
                world = world,
                accent = accent,
                protectedSeconds = protectedSeconds,
                isOwner = isOwner,
                onPrimary = if (isOwner) onShareReign else onChallenge,
            )
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
            OneLogo(accent, compact = true)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(6.dp))
            Text("LIVE", color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatNumber(world.liveWatchers), color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            Text(" WATCHING", color = Muted, fontFamily = mono, fontSize = 8.sp)
            Spacer(Modifier.width(13.dp))
            CircleAction("↗", onShare)
            Spacer(Modifier.width(7.dp))
            CircleAction("···", onReport)
        }
    }
}

@Composable
private fun MessageStage(message: String, accent: Color) {
    val size = when {
        message.length <= 22 -> 52.sp
        message.length <= 42 -> 44.sp
        message.length <= 62 -> 38.sp
        else -> 33.sp
    }
    Column {
        Text(
            message,
            color = Paper,
            fontFamily = display,
            fontWeight = FontWeight.Black,
            fontSize = size,
            lineHeight = size * .96f,
            letterSpacing = (-1.8).sp,
        )
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(42.dp).height(4.dp).background(accent, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text("THE ONLY LIVE MESSAGE", color = accent, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.3.sp)
        }
    }
}

@Composable
private fun ViewLedger(world: WorldState, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = .2f))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatNumber(world.appViews + world.webViews), color = Paper, fontWeight = FontWeight.Black, fontSize = 23.sp, letterSpacing = (-.5).sp)
            Text("VERIFIED VIEWS THIS REIGN", color = Muted, fontFamily = mono, fontSize = 7.sp, letterSpacing = .8.sp)
        }
        LedgerMetric("APP", world.appViews, accent)
        Spacer(Modifier.width(16.dp))
        LedgerMetric("WEB", world.webViews, Ice)
    }
}

@Composable
private fun CrowdControls(
    world: WorldState,
    onReact: (String) -> Unit,
    onEcho: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("THIEF", "TOO SLOW", "TAKE IT BACK", "RESPECT", "LOL").forEach { reaction ->
                Surface(
                    modifier = Modifier.clickable { onReact(reaction) },
                    color = Color.White.copy(alpha = .055f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
                ) {
                    Text(reaction, color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                }
            }
            Surface(
                modifier = Modifier.clickable(onClick = onEcho),
                color = Acid.copy(alpha = .12f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Acid.copy(alpha = .35f)),
            ) {
                Text("ECHO THEIR WORDS", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 7.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
            }
        }
        world.reactions.firstOrNull()?.let { latest ->
            Text("${latest.handle}: ${latest.reaction}", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.padding(start = 4.dp, top = 7.dp))
        }
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF17171D), Color(0xFF0B0B0E))))
            .border(1.dp, accent.copy(alpha = .32f), RoundedCornerShape(24.dp))
            .padding(17.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(if (isOwner) "YOUR REIGN" else if (cooldown == 0) "FREE STEAL READY" else "YOUR COOLDOWN", color = Muted, fontFamily = mono, fontSize = 8.sp, letterSpacing = 1.2.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (isOwner) formatDuration((System.currentTimeMillis() - world.reign.startedAtMillis) / 1_000L) else if (cooldown == 0) "READY" else "${cooldown}s", color = Paper, fontWeight = FontWeight.Black, fontSize = 35.sp, letterSpacing = (-1).sp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isOwner) "LIVE" else if (cooldown == 0) "FREE" else "LEFT", color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (protectedSeconds > 0) "LANDING" else "LIVE BATTLE", color = if (protectedSeconds > 0) Orange else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                Text(if (protectedSeconds > 0) "${protectedSeconds}s" else "${world.takeoversToday} TAKES TODAY", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(7.dp))
                DecaySparkline(accent, Modifier.width(86.dp).height(22.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPrimary,
            enabled = isOwner || canTake,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = Ink,
                disabledContainerColor = Color.White.copy(alpha = .08f),
                disabledContentColor = Muted,
            ),
        ) {
            Text(
                when {
                    isOwner -> "BROADCAST YOUR REIGN  ↗"
                    protectedSeconds > 0 -> "TAKEOVER LANDS IN ${protectedSeconds}s"
                    cooldown == 0 -> "STEAL ONE — FREE  →"
                    canRevenge -> "REVENGE NOW — 1 TICKET  →"
                    else -> "FREE STEAL RECHARGES IN ${cooldown}s"
                },
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = .4.sp,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            if (world.connected) "${world.credits} REVENGE TICKETS // STALE-RACE LOSERS SPEND NOTHING" else if (world.demoMode) "LOCAL DEMO // CONNECT SUPABASE FOR GLOBAL PLAY" else "OFFLINE // RECONNECTING TO ONE",
            color = Muted,
            fontFamily = mono,
            fontSize = 7.sp,
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
        SectionHeader("MESSAGE LIBRARY", "${world.messages.count { it.status == MessageStatus.APPROVED }} APPROVED")
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
            .clip(RoundedCornerShape(21.dp))
            .background(InkRaised)
            .border(1.dp, if (selected) statusColor.copy(alpha = .65f) else Color.White.copy(alpha = .06f), RoundedCornerShape(21.dp))
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
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        SectionHeader("HALL OF ONE", "SEASON 01")
        Spacer(Modifier.height(24.dp))
        Text("FAME OUTLIVES\nTHE REIGN.", color = Paper, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 40.sp, letterSpacing = (-1.6).sp)
        Text("The live object never resets. The legends do—every day at 00:00 UTC.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 11.dp))
        Spacer(Modifier.height(22.dp))
        HallHero(world.hall.first())
        Spacer(Modifier.height(12.dp))
        world.hall.drop(1).forEach { entry ->
            HallRow(entry)
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(formatNumber(world.appViews + world.webViews), "LIVE REIGN VIEWS", Modifier.weight(1f))
            StatBlock(formatNumber(world.takeoversToday), "TAKEOVERS TODAY", Modifier.weight(1f))
        }
    }
}

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
    onOpenPrivacy: () -> Unit,
    onUnblockAll: () -> Unit,
    onDeleteAccount: () -> Unit,
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
        SectionHeader("YOUR ONE", "FOUNDING SURVIVOR")
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(Acid), contentAlignment = Alignment.Center) {
                Text(initials, color = Ink, fontWeight = FontWeight.Black, fontSize = 25.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 28.sp)
                Text(if (world.connected) "${user?.city ?: "EARTH"}, ${user?.countryCode ?: "XX"}  ◆ GLOBAL ID" else "CONNECTING TO GLOBAL ID", color = Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = .8.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        WalletHero(world.credits, onVault)
        Spacer(Modifier.height(12.dp))
        DailyCapCard(world)
        Spacer(Modifier.height(12.dp))
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
                else -> "DEMO"
            },
            accent = Orange,
            onClick = onEnablePush,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "REVENUECAT REVENGE",
            detail = if (revenueCatReady) "Live ticket packs are connected and server verified." else "Add the public SDK key to activate ticket packs.",
            badge = if (revenueCatReady) "LIVE" else "DEMO",
            accent = Acid,
            onClick = onVault,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "INVITE THE AUDIENCE",
            detail = "Every new viewer makes the only screen more valuable.",
            badge = "SHARE",
            accent = Ice,
            onClick = onShareONE,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "PRIVACY & DATA",
            detail = "Read exactly what ONE collects, publishes and retains.",
            badge = "OPEN",
            accent = Ice,
            onClick = onOpenPrivacy,
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
        Text(if (world.demoMode) "LOCAL DEMO MODE" else if (world.connected) "GLOBAL LEDGER CONNECTED" else "GLOBAL LEDGER OFFLINE", color = if (world.connected && !world.demoMode) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.2.sp)
        Text(if (world.demoMode) "Add Supabase public configuration to make every phone fight over the same ONE." else "Ownership, cooldowns, tickets, views and race ordering are controlled by the server.", color = Muted, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 7.dp))
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
) {
    val accent = Acid
    val needsTicket = world.cooldownRemainingSeconds > 0
    val canAttempt = !needsTicket || world.credits > 0
    val selected = world.messages.firstOrNull { it.id == ui.selectedMessageId }
    val busy = ui.challengePhase !in listOf(ChallengePhase.IDLE, ChallengePhase.FAILED)

    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(accent, Modifier.fillMaxSize().graphicsLayer { alpha = .45f })
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            OverlayHeader("TAKE ONE", "RACE-SAFE RESERVATION", onClose)
            Spacer(Modifier.height(24.dp))
            Text("ONE SCREEN.\nONE WINNER.", color = Paper, fontWeight = FontWeight.Black, fontSize = 45.sp, lineHeight = 42.sp, letterSpacing = (-1.6).sp)
            Text("The first server-verified steal commits. A stale-race loser spends nothing.", color = Muted, fontFamily = mono, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(21.dp))

            RaceCard(world)
            Spacer(Modifier.height(17.dp))
            Text("CHOOSE YOUR APPROVED MESSAGE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                world.messages.filter { it.status == MessageStatus.APPROVED }.forEach { message ->
                    SelectableMessage(message, selected = message.id == ui.selectedMessageId) {
                        if (!busy) onSelectMessage(message.id)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            AnimatedContent(ui.challengePhase, label = "challenge-phase") { phase ->
                when (phase) {
                    ChallengePhase.IDLE, ChallengePhase.FAILED -> Column {
                        if (phase == ChallengePhase.FAILED) {
                            ErrorStrip(ui.challengeStatus)
                            Spacer(Modifier.height(11.dp))
                        }
                        if (!canAttempt) {
                            PrimaryButton("FREE STEAL IN ${world.cooldownRemainingSeconds}s", Ice, onOpenVault)
                        } else {
                            HoldToOwnButton(
                                text = if (needsTicket) "HOLD TO REVENGE  •  1 TICKET" else "HOLD TO STEAL ONE  •  FREE",
                                enabled = selected != null,
                                onComplete = onBegin,
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("${world.credits} REVENGE TICKETS  //  ${if (needsTicket) "ONE SPENT ONLY IF YOU WIN" else "FREE TAKE READY"}", color = Muted, fontFamily = mono, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    ChallengePhase.WON -> WonPanel(selected?.text.orEmpty())
                    else -> ProtocolProgress(phase, ui.challengeStatus)
                }
            }
            Spacer(Modifier.height(18.dp))
            RuleStrip("free steal cooldown", "instant revenge ticket", "atomic race")
        }
        if (ui.challengePhase == ChallengePhase.WON) TakeoverFlash(Modifier.fillMaxSize())
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

    Box(Modifier.fillMaxSize().background(Acid)) {
        CertificateField(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OneLogo(Ink, compact = true)
                Text(if (stillOwner) "LIVE OWNERSHIP" else "REIGN COMPLETE", color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
                CircleActionDark("×", onClose)
            }
            Spacer(Modifier.height(34.dp))
            Text(if (stillOwner) "YOU OWNED\nTHE INTERNET." else "YOU WERE\nTHE INTERNET.", color = Ink, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 44.sp, letterSpacing = (-2).sp)
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(24.dp)).padding(20.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CERTIFICATE OF ONE", color = Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        Text("SERVER-VERIFIED*", color = Ice, fontFamily = mono, fontSize = 8.sp)
                    }
                    Spacer(Modifier.height(25.dp))
                    Text("“${resolved.message}”", color = Paper, fontWeight = FontWeight.Black, fontSize = 25.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ReceiptMetric(formatNumber(total), "VERIFIED VIEWS")
                        ReceiptMetric(formatDuration(duration.toLong()), "REIGN")
                        ReceiptMetric(resolved.paidCredits.toString(), "REVENGE TICKETS")
                    }
                    Spacer(Modifier.height(21.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOOK FROM ${resolved.previousOwner}", color = Muted, fontFamily = mono, fontSize = 8.sp)
                        Text(resolved.dethronedBy?.let { "LOST TO $it" } ?: "STATUS: LIVE", color = if (resolved.dethronedBy == null) Acid else Orange, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            PrimaryButton("BROADCAST THE PROOF  ↗", Ink, onShare, foreground = Acid)
            Spacer(Modifier.height(10.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClose), color = Color.Transparent, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Ink.copy(alpha = .35f))) {
                Text("RETURN TO THE LIVE SCREEN", color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 10.sp, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("*Issued from the authoritative global takeover ledger.", color = Ink.copy(alpha = .56f), fontFamily = mono, fontSize = 7.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    ui: OneUiState,
    revenueCatReady: Boolean,
    onClose: () -> Unit,
    onWatchAd: () -> Unit,
    onPurchase: (Int) -> Unit,
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
        OverlayHeader("ONE VAULT", if (revenueCatReady) "REVENUECAT LIVE" else "SAFE DEMO MODE", onClose)
        Spacer(Modifier.height(24.dp))
        Text("${formatNumber(world.credits)}", color = Acid, fontWeight = FontWeight.Black, fontSize = 66.sp, letterSpacing = (-3).sp)
        Text("REVENGE TICKETS", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Text("Tickets skip your cooldown. They have no cash value and cannot leave ONE.", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(23.dp))

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF193847), Color(0xFF0A151B)))).border(1.dp, Ice.copy(alpha = .45f), RoundedCornerShape(24.dp)).padding(18.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatusPill("CATVERTISING", Ice)
                    Text("+1 TICKET", color = Ice, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(Modifier.height(20.dp))
                Text("INTERCEPT A\nSPONSOR SIGNAL.", color = Paper, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 29.sp)
                Text("Verified rewarded ad. Capped daily. No spoofable client reward.", color = Muted, fontFamily = mono, fontSize = 9.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 9.dp))
                Spacer(Modifier.height(16.dp))
                if (ui.adPlaying) {
                    LinearProgressIndicator(progress = { ui.adProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Ice, trackColor = Color.White.copy(alpha = .12f))
                    Text("VERIFYING SERVER-SIDE REWARD…", color = Ice, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, modifier = Modifier.padding(top = 9.dp))
                } else {
                    PrimaryButton("WATCH TRANSMISSION  +1", Ice, onWatchAd)
                }
            }
        }
        Spacer(Modifier.height(21.dp))
        Text("REVENGE PACKS", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 8.sp, letterSpacing = 1.1.sp)
        Spacer(Modifier.height(9.dp))
        CreditPack("SPARK", 3, "£0.99", false) { onPurchase(3) }
        CreditPack("CHALLENGER", 20, "£4.99", true) { onPurchase(20) }
        CreditPack("HEADLINER", 50, "£9.99", false) { onPurchase(50) }
        Spacer(Modifier.height(14.dp))
        Text("Live prices come from RevenueCat. Grants are mirrored only after the signed server webhook; this APK never contains a secret key.", color = Muted, fontFamily = mono, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 12.sp, modifier = Modifier.fillMaxWidth())
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xF20E0E11))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("●", "LIVE", MainTab.LIVE, selected, onSelected)
        NavItem("≡", "WORDS", MainTab.LIBRARY, selected, onSelected)
        NavItem("♛", "HALL", MainTab.HALL, selected, onSelected)
        NavItem("○", "YOU", MainTab.YOU, selected, onSelected)
    }
}

@Composable
private fun NavItem(icon: String, label: String, tab: MainTab, selected: MainTab, onSelected: (MainTab) -> Unit) {
    val active = tab == selected
    Column(
        Modifier
            .clip(RoundedCornerShape(15.dp))
            .clickable { onSelected(tab) }
            .background(if (active) Acid.copy(alpha = .1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, color = if (active) Acid else Muted, fontWeight = FontWeight.Black, fontSize = 14.sp)
        Text(label, color = if (active) Paper else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp, letterSpacing = .6.sp)
    }
}

@Composable
private fun LiveField(accent: Color, modifier: Modifier = Modifier) {
    // A device-sized radial shader made first render catastrophically slow on some
    // Android GPUs. Keep the field intentionally minimal: ONE's typography and live
    // state carry the visual drama, while the background stays cheap and responsive.
    Box(modifier.background(Ink).background(accent.copy(alpha = .035f)))
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
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Color(0xFF24242A) else Color(0xFF151518))
            .border(1.dp, if (enabled) Acid.copy(alpha = .55f) else Muted.copy(alpha = .2f), RoundedCornerShape(16.dp))
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(19.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
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
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Acid else InkRaised)
            .border(1.dp, if (selected) Acid else Color.White.copy(alpha = .07f), RoundedCornerShape(17.dp))
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
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Acid, Color(0xFFB9E800)))).padding(20.dp)) {
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
private fun HallRow(entry: HallEntry) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#${entry.rank.toString().padStart(2, '0')}", color = if (entry.rank == 2) Ice else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 13.sp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.message, color = Paper, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${entry.owner.handle}  //  ${formatNumber(entry.verifiedViews)} VIEWS", color = Muted, fontFamily = mono, fontSize = 7.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text(formatDuration(entry.reignSeconds.toLong()), color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun WalletHero(credits: Int, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF27272E), Color(0xFF111115)))).border(1.dp, Acid.copy(alpha = .33f), RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(19.dp)) {
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkRaised).border(1.dp, accent.copy(alpha = .14f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(detail, color = Muted, fontFamily = mono, fontSize = 8.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(badge, color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 8.sp)
    }
}

@Composable
private fun CreditPack(name: String, amount: Int, price: String, popular: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(17.dp)).background(if (popular) Acid.copy(alpha = .09f) else InkRaised).border(1.dp, if (popular) Acid.copy(alpha = .48f) else Color.White.copy(alpha = .06f), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Paper, fontWeight = FontWeight.Black, fontSize = 14.sp)
                if (popular) {
                    Spacer(Modifier.width(7.dp))
                    StatusPill("BEST VALUE", Acid)
                }
            }
            Text("${formatNumber(amount)} REVENGE TICKETS", color = Muted, fontFamily = mono, fontSize = 8.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(price, color = if (popular) Acid else Paper, fontWeight = FontWeight.Black, fontSize = 16.sp)
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
    Row(Modifier.fillMaxWidth().background(Orange).statusBarsPadding().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("!", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("TAP FOR THE RECEIPT — THEN TAKE IT BACK", color = Ink.copy(alpha = .65f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 7.sp)
        }
        Text("×", color = Ink, fontSize = 20.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp))
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
private fun ReceiptMetric(value: String, label: String) {
    Column {
        Text(value, color = Paper, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(label, color = Muted, fontFamily = mono, fontSize = 6.sp)
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
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = foreground), shape = RoundedCornerShape(14.dp)) {
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
