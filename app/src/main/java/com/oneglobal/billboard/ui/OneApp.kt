package com.oneglobal.billboard.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.oneglobal.billboard.BuildConfig
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Flag

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oneglobal.billboard.OneViewModel
import com.oneglobal.billboard.R
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
private val display = FontFamily(Font(R.font.anton))

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OneApp(
    viewModel: OneViewModel,
    revenueCatReady: Boolean,
    oneSignalReady: Boolean,
    onPurchaseCredits: (Int, (Boolean, String, Int) -> Unit) -> Unit,
    onRestorePurchases: ((Boolean, String) -> Unit) -> Unit,
    onRequestPush: ((Boolean) -> Unit) -> Unit,
    onDisablePush: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onShareReceipt: (ReignReceipt) -> Unit,
    onShareONE: () -> Unit,
    onGoogle: (Boolean) -> Unit,
    onWatchAd: () -> Unit,
    onIdentifyUser: (String) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDeletionHelp: () -> Unit,
    onBuyOnWeb: () -> Unit,
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
        LaunchScreen()
        return
    }

    // A banned/suspended account keeps watching normally - only the take
    // action itself is blocked, via Overlay.RESTRICTED opened from
    // openChallenge(). No full-screen gate here on purpose.
    BackHandler(ui.overlay != Overlay.NONE) { viewModel.closeOverlay() }

    LaunchedEffect(ui.takeoverPulse) {
        if (ui.takeoverPulse > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(world.currentUserId, oneSignalReady, revenueCatReady) {
        if (world.currentUserId.isNotBlank()) {
            onIdentifyUser(world.currentUserId)
            viewModel.maybePromptHandleOnOpen()
        }
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
        OnboardingScreen(world = world, onTakeIt = viewModel::completeOnboardingAndCompose)
        return
    }

    GuidedTourHost(currentTab = ui.tab, startOnLaunch = ui.howToPlayOnLaunch, onSelectTab = viewModel::selectTab) { startTour ->
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
                    onUnblockCurrentOwner = { viewModel.unblockOne(world.reign.owner.id) },
                    offline = ui.offlineVisible,
                    reconnecting = ui.reconnecting,
                    onReconnect = viewModel::reconnect,
                )
                MainTab.LIBRARY -> LibraryScreen(
                    world = world,
                    selectedId = ui.selectedMessageId,
                    onCompose = viewModel::openCompose,
                    onDeploy = { id ->
                        viewModel.selectMessage(id)
                        viewModel.openChallenge()
                    },
                    onDelete = viewModel::deleteMessage,
                    offline = ui.offlineVisible,
                    reconnecting = ui.reconnecting,
                    onReconnect = viewModel::reconnect,
                )
                MainTab.HALL -> HallScreen(
                    world,
                    onTakeIt = { viewModel.selectTab(MainTab.LIVE) },
                    offline = ui.offlineVisible,
                    reconnecting = ui.reconnecting,
                    onReconnect = viewModel::reconnect,
                )
                MainTab.YOU -> YouScreen(
                    world = world,
                    ui = ui,
                    revenueCatReady = revenueCatReady,
                    oneSignalReady = oneSignalReady,
                    onVault = viewModel::openVault,
                    onEnablePush = { viewModel.toggleRevengeAlerts(onRequestPush, onDisablePush, onOpenNotificationSettings) },
                    onShareONE = onShareONE,
                    onEditHandle = viewModel::openHandleEditor,
                    onIdentityBackup = viewModel::openIdentityBackup,
                    onOpenPrivacy = onOpenPrivacy,
                    onFeedback = viewModel::openFeedback,
                    onUnblockAll = viewModel::unblockAll,
                    onUnblockOne = viewModel::unblockOne,
                    onDeleteAccount = viewModel::openDeleteAccount,
                    onHowItWorks = viewModel::openHowItWorks,
                    onEditProfile = viewModel::openProfile,
                )
            }
        }

        BottomNav(
            selected = ui.tab,
            onSelected = viewModel::selectTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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
                Overlay.PROFILE -> ProfileOverlay(world, ui, viewModel::closeOverlay, viewModel::saveProfile, viewModel::updateProfilePhoto)
                Overlay.CHALLENGE -> ChallengeOverlay(
                    world = world,
                    ui = ui,
                    onClose = viewModel::closeOverlay,
                    onSelectMessage = viewModel::selectMessage,
                    onBegin = viewModel::beginChallenge,
                    onOpenVault = viewModel::openVault,
                    onInfo = viewModel::openHowItWorks,
                    onWatchAd = onWatchAd,
                    onAdOfferShown = viewModel::adOfferShown,
                    onBypassWithCredits = viewModel::bypassTakeRefillWithCredits,
                )
                Overlay.RESTRICTED -> RestrictedOverlay(
                    world = world,
                    onClose = viewModel::closeOverlay,
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
                    handle = world.currentUser?.handle.orEmpty(),
                    onGoogle = onGoogle,
                    onHandleChanged = viewModel::updateRecoveryHandle,
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
                            viewModel.showToast(message)
                            if (success && granted > 0) viewModel.grantPurchasedCredits(granted)
                        }
                    },
                    onRestore = {
                        onRestorePurchases { success, message ->
                            viewModel.showToast(message)
                            if (success) viewModel.grantPurchasedCredits(0)
                        }
                    },
                    onBuyOnWeb = onBuyOnWeb,
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
                Overlay.HOW_IT_WORKS -> HowItWorksOverlay(
                    enabledOnLaunch = ui.howToPlayOnLaunch,
                    onEnabledChanged = viewModel::setHowToPlayOnLaunch,
                    onStartNow = {
                        viewModel.closeOverlay()
                        startTour()
                    },
                    onClose = viewModel::closeOverlay,
                )
            }
        }

        // Rendered last so a dethroning interrupts whatever screen or overlay is currently open,
        // instead of being silently hidden behind it.
        AnimatedVisibility(
            visible = ui.revengeBanner != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            RevengeBanner(
                world = world,
                onOpen = viewModel::takeItBack,
                onDismiss = viewModel::dismissRevenge,
            )
        }

        if (ui.pushPromptVisible) {
            PushPermissionPrompt(
                onAccept = { viewModel.acceptPushPrompt(onRequestPush) },
                onDismiss = viewModel::dismissPushPrompt,
            )
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
            Text("1", color = Acid, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 154.sp, lineHeight = 136.sp, letterSpacing = (-10).sp)
            Text("ONE", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 7.sp)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CONNECTING TO THE WORLD", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(2.dp).background(Color.White.copy(alpha = .08f))) {
                Box(Modifier.fillMaxWidth(.72f).fillMaxHeight().background(Acid))
            }
        }
    }
}

// Three cards, one build-up: a concept (one screen), a specific person
// (whoever really holds it right now, pulled live), then the action itself.
// Card 3's button doesn't end onboarding into an empty live screen - it's
// the first real step of playing. A brand-new account has no approved
// message yet, so that step is writing one, not attempting a take that has
// nothing to submit.
@Composable
private fun OnboardingScreen(world: WorldState, onTakeIt: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(0) }
    val colors = listOf(Acid, Orange, Ice)
    val color = colors[page]
    val liveOwnerKnown = world.connected && world.reign.owner.id != "system"
    val handle = world.reign.owner.handle

    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(color, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            repeat(7) { ring ->
                drawCircle(color.copy(alpha = .035f), size.minDimension * (.18f + ring * .09f), Offset(size.width * .78f, size.height * .22f), style = Stroke(1f))
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp, vertical = 17.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ONE / FIRST ENTRY", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Text("${page + 1} / 3", color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(3.dp).background(if (index <= page) color else Color.White.copy(alpha = .09f)))
                }
            }
            AnimatedContent(page, transitionSpec = { (fadeIn(tween(280)) + slideInVertically { it / 5 }) togetherWith (fadeOut(tween(180)) + slideOutVertically { -it / 5 }) }, label = "onboarding-page", modifier = Modifier.weight(1f)) { shownPage ->
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(.45f))
                    when (shownPage) {
                        0 -> {
                            OnboardingReveal(delayMs = 0, bounce = true) { revealModifier ->
                                Box(revealModifier.size(132.dp).clip(CircleShape).background(color).shadow(28.dp, CircleShape, spotColor = color.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                                    Text("◉", color = Ink, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 54.sp)
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            OnboardingReveal(delayMs = 90) { revealModifier ->
                                Text("THERE IS\nONE SCREEN.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 39.sp, letterSpacing = (-1.8).sp, modifier = revealModifier)
                            }
                            OnboardingReveal(delayMs = 170) { revealModifier ->
                                Text("Everyone using ONE sees the same message. There is no feed.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = revealModifier.padding(top = 15.dp, end = 24.dp))
                            }
                        }
                        1 -> {
                            OnboardingReveal(delayMs = 0, bounce = true) { revealModifier ->
                                if (liveOwnerKnown) {
                                    ProfilePhoto(world.reign.owner, color, revealModifier.size(132.dp).shadow(28.dp, CircleShape, spotColor = color.copy(alpha = .4f)))
                                } else {
                                    Box(revealModifier.size(132.dp).clip(CircleShape).background(color.copy(alpha = .3f)), contentAlignment = Alignment.Center) {
                                        Text("…", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 40.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            OnboardingReveal(delayMs = 90) { revealModifier ->
                                Text(
                                    if (liveOwnerKnown) "$handle\nOWNS IT RIGHT NOW." else "SOMEONE OWNS\nIT RIGHT NOW.",
                                    color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 39.sp, lineHeight = 37.sp, letterSpacing = (-1.5).sp,
                                    modifier = revealModifier,
                                )
                            }
                            OnboardingReveal(delayMs = 170) { revealModifier ->
                                Text("One person holds it at a time. Everyone else is looking at their words.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = revealModifier.padding(top = 15.dp, end = 24.dp))
                            }
                            if (liveOwnerKnown) {
                                OnboardingReveal(delayMs = 250) { revealModifier ->
                                    Text(
                                        "“${world.reign.message.text}”",
                                        color = color, fontWeight = FontWeight.Black, fontSize = 15.sp, lineHeight = 19.sp,
                                        modifier = revealModifier.padding(top = 14.dp, end = 24.dp),
                                    )
                                }
                            }
                        }
                        else -> {
                            OnboardingReveal(delayMs = 0, bounce = true) { revealModifier ->
                                Box(revealModifier.size(132.dp).clip(CircleShape).background(color).shadow(28.dp, CircleShape, spotColor = color.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                                    Text("ϟ", color = Ink, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 54.sp)
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            OnboardingReveal(delayMs = 90) { revealModifier ->
                                Text("SO TAKE\nIT OFF THEM.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 39.sp, letterSpacing = (-1.8).sp, modifier = revealModifier)
                            }
                            OnboardingReveal(delayMs = 170) { revealModifier ->
                                Text("Your words replace theirs, instantly, for everyone watching.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = revealModifier.padding(top = 15.dp, end = 24.dp))
                            }
                        }
                    }
                    Spacer(Modifier.weight(.35f))
                }
            }
            Button(
                onClick = { if (page < 2) page++ else onTakeIt() },
                modifier = Modifier.fillMaxWidth().height(62.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Ink),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    when {
                        page < 2 -> "CONTINUE  →"
                        liveOwnerKnown -> "TAKE IT FROM $handle  →"
                        else -> "TAKE IT  →"
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                )
            }
            if (page > 0) Text("BACK", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { page-- }.padding(13.dp))
            else Spacer(Modifier.height(34.dp))
        }
    }
}

// Staggers each element in on its own beat instead of the whole card arriving
// at once - `remember` (not `remember(key)`) is deliberate: this composable's
// call site already lives inside AnimatedContent's per-`shownPage` lambda, so
// it's freshly created (and its animation freshly starts) every time a new
// onboarding page is shown, with no extra key needed.
@Composable
private fun OnboardingReveal(delayMs: Int, bounce: Boolean = false, content: @Composable (Modifier) -> Unit) {
    val density = LocalDensity.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        reveal.animateTo(1f, spring(dampingRatio = if (bounce) .55f else .82f, stiffness = if (bounce) 260f else 340f))
    }
    content(
        Modifier.graphicsLayer {
            alpha = reveal.value.coerceIn(0f, 1f)
            translationY = with(density) { (1f - reveal.value.coerceIn(0f, 1f)) * 20.dp.toPx() }
            if (bounce) {
                scaleX = .82f + reveal.value * .18f
                scaleY = .82f + reveal.value * .18f
            }
        },
    )
}

@Composable
private fun OnboardingStep(index: String, icon: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(index, color = Muted, fontFamily = mono, fontSize = 12.sp)
        Text(icon, color = Acid, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(vertical = 3.dp))
        Text(label, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .7.sp)
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
    onUnblockCurrentOwner: () -> Unit,
    offline: Boolean,
    reconnecting: Boolean,
    onReconnect: () -> Unit,
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
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 100.dp),
        ) {
            LiveHeader(world, accent, onShareONE, onReport)
            Spacer(Modifier.height(20.dp))
            if (!world.currentContentBlocked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(world.reign.owner, accent, Modifier.size(58.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        if (world.reign.owner.verified) {
                            Spacer(Modifier.width(5.dp))
                            Text("◆", color = accent, fontSize = 12.sp)
                        }
                    }
                    Text(
                        world.reign.owner.locationLabel(),
                        color = Muted,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
            if (offline) {
                Spacer(Modifier.height(8.dp))
                LastSeenTag(world.lastConnectedAtMillis)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 17.dp).height(1.dp).background(Color.White.copy(alpha = .16f)))
            LiveMapMessageStage(
                message = world.reign.message.text,
                accent = accent,
                connected = world.connected,
                city = world.reign.owner.city,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp).padding(vertical = 12.dp),
            )
            } else {
                HiddenOwnerStage(accent, onUnblockCurrentOwner)
            }
            ViewLedger(world, accent, protectedSeconds, offline)
            Spacer(Modifier.height(12.dp))
            AuctionCard(
                world = world,
                accent = accent,
                protectedSeconds = protectedSeconds,
                isOwner = isOwner,
                onPrimary = if (isOwner) onShareReign else onChallenge,
                offline = offline,
            )
            Spacer(Modifier.height(14.dp))
            CrowdControls(world, onReact, onEcho)
        }

        if (offline) {
            OfflineScrim(Modifier.matchParentSize())
            OfflineStalledBar(Modifier.align(Alignment.TopCenter))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
            ) {
                WaitingForConnectionPill()
                Spacer(Modifier.height(10.dp))
                OfflineCard(
                    lastConnectedAtMillis = world.lastConnectedAtMillis,
                    reconnecting = reconnecting,
                    onReconnect = onReconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HiddenOwnerStage(accent: Color, onUnblock: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)
            .clip(RoundedCornerShape(7.dp)).background(InkRaised)
            .border(1.dp, accent.copy(alpha = .42f), RoundedCornerShape(7.dp)).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CONTENT HIDDEN", color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
        Text("YOU'VE HIDDEN\nTHIS OWNER.", color = Paper, fontFamily = display, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 13.dp))
        Text("Their message is live for everyone else. The game continues — you can still take the screen.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        Surface(modifier = Modifier.clickable(onClick = onUnblock), color = Color.Transparent, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, accent.copy(alpha = .5f))) {
            Text("UNBLOCK TO SEE THIS AGAIN", color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun LiveHeader(world: WorldState, accent: Color, onShare: () -> Unit, onReport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Text("LIVE WORLD STATE", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .7.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.Filled.Share, "Share", onShare)
            Spacer(Modifier.width(7.dp))
            CircleAction(Icons.Filled.Flag, "Report", onReport)
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
        Image(
            painter = painterResource(R.drawable.one_world_map_halftone),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = .24f,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(vertical = 12.dp)) {
            MessageStage(message = message, accent = accent)
        }
    }
}

private val messageStageSizeSteps = listOf(46.sp, 42.sp, 38.sp, 34.sp, 30.sp, 27.sp, 24.sp, 22.sp, 20.sp, 18.sp)

// Same technique as MessageStage below, generalized: picks the largest size in
// [sizeSteps] where the widest real word in [text] still fits the available
// width, so real (variable-length) content never gets stuck at a size that's
// too big for the device it's actually rendering on.
@Composable
private fun AutoFitMessageText(text: String, color: Color, sizeSteps: List<TextUnit>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val words = remember(text) { text.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    BoxWithConstraints(modifier) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val chosenSize = remember(words, maxWidthPx) {
            sizeSteps.firstOrNull { candidate ->
                if (maxWidthPx <= 0f || words.isEmpty()) return@firstOrNull true
                val style = TextStyle(fontWeight = FontWeight.Black, fontSize = candidate)
                val longestWordWidth = words.maxOf { word -> textMeasurer.measure(word, style, maxLines = 1, softWrap = false).size.width }
                longestWordWidth <= maxWidthPx
            } ?: sizeSteps.last()
        }
        Text(text, color = color, fontWeight = FontWeight.Black, fontSize = chosenSize, lineHeight = chosenSize * 1.15f)
    }
}

@Composable
private fun MessageStage(message: String, accent: Color) {
    val upper = message.uppercase()
    val words = remember(upper) { upper.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        // Pick the largest size where no single word is wider than the screen -
        // a length-only heuristic can't know that (device width, real glyph
        // widths, hashtags/URLs with no spaces to wrap at), and picking too
        // large a size forces an ugly mid-word character break instead of a
        // clean wrap onto the next line.
        val chosenSize = remember(words, maxWidthPx) {
            messageStageSizeSteps.firstOrNull { candidate ->
                if (maxWidthPx <= 0f || words.isEmpty()) return@firstOrNull true
                val style = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = candidate, letterSpacing = (-.5).sp)
                val longestWordWidth = words.maxOf { word ->
                    textMeasurer.measure(word, style, maxLines = 1, softWrap = false).size.width
                }
                longestWordWidth <= maxWidthPx
            } ?: messageStageSizeSteps.last()
        }

        Text(
            upper,
            color = Paper,
            fontFamily = display,
            fontWeight = FontWeight.Black,
            fontSize = chosenSize,
            lineHeight = chosenSize * 1.05f,
            letterSpacing = (-.5).sp,
        )
    }
}

@Composable
private fun ViewLedger(world: WorldState, accent: Color, protectedSeconds: Int, offline: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = .2f))
            .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveMetric("◉", "WATCHING NOW", if (offline) "—" else formatNumber(world.liveWatchers), if (world.liveWatchers == 1) "PERSON" else "PEOPLE", accent, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(62.dp).background(Color.White.copy(alpha = .16f)))
        LiveMetric(
            "◷",
            if (protectedSeconds > 0) "PROTECTION" else "SCREEN STATUS",
            if (offline) "—" else if (protectedSeconds > 0) formatDuration(protectedSeconds.toLong()) else "LIVE",
            if (protectedSeconds > 0) "MIN : SEC" else "OPEN TO TAKE",
            accent,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveMetric(icon: String, label: String, value: String, footer: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Text("$icon  $label", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(value, color = color, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 28.sp, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 3.dp))
        Text(footer, color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = .8.sp)
    }
}

@Composable
private fun CrowdControls(
    world: WorldState,
    onReact: (String) -> Unit,
    onEcho: () -> Unit,
) {
    val targets = LocalTourTargets.current
    Column(Modifier.onGloballyPositioned { targets["REACT"] = it.boundsInRoot() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE REACTIONS", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp)
            Text(world.reactionCounts?.values?.sum()?.let { "${formatNumber(it)} TOTAL" } ?: "SYNCING", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("🔥" to "FIRE", "👏" to "RESPECT", "💯" to "100", "👀" to "WATCH", "🚀" to "ROCKET").forEach { (icon, key) ->
                ReactionOrb(icon, key, world.reactionCounts?.get(key), key in world.myReactions) { onReact(key) }
            }
        }
    }
}

@Composable
private fun ReactionOrb(icon: String, label: String, count: Int?, selected: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(InkRaised)
                .border(1.dp, if (selected) Acid else Color.White.copy(alpha = .1f), CircleShape)
                .clickable(enabled = !selected, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(icon, fontSize = 24.sp)
                Text(count?.let(::formatNumber) ?: "—", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

    }
}

// Ticks down locally between polls instead of jumping in ~1.5s steps, and
// resnaps to the real server value the moment a fresh one arrives.
@Composable
private fun rememberCountdownSeconds(serverSeconds: Int): Int {
    var remaining by remember(serverSeconds) { mutableStateOf(serverSeconds) }
    LaunchedEffect(serverSeconds) {
        remaining = serverSeconds
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    return remaining
}

@Composable
private fun AuctionCard(
    world: WorldState,
    accent: Color,
    protectedSeconds: Int,
    isOwner: Boolean,
    onPrimary: () -> Unit,
    offline: Boolean = false,
) {
    val hasBalance = world.takeBalance > 0
    val refillSeconds = rememberCountdownSeconds(world.takeRefillSeconds)
    // The server only collects a completed refill into a real, spendable
    // balance inside take_one() itself - it's never applied just by polling
    // get_one_state(). Gating the button on takeBalance alone would mean
    // once balance hits zero and the timer finishes, nothing ever calls
    // take_one() again to collect it: a permanent deadlock. Treating the
    // countdown reaching zero as takeable too lets the tap through, and
    // take_one() correctly collects the refill and processes the take in
    // the same call.
    val refillComplete = !hasBalance && refillSeconds <= 0
    val canTake = protectedSeconds == 0 && (hasBalance || refillComplete)
    val targets = LocalTourTargets.current
    Column(Modifier.fillMaxWidth().onGloballyPositioned { targets["TAKE"] = it.boundsInRoot() }) {
        if (!isOwner) {
            TakeBalancePips(world.takeBalance, world.takeBalanceCap, refillSeconds, accent)
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onPrimary,
            enabled = !offline && (isOwner || canTake),
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
                    offline -> "⊘  OFFLINE — CAN'T TAKE THE SCREEN"
                    isOwner -> "⚡  BROADCAST YOUR REIGN"
                    protectedSeconds > 0 -> "TAKEOVER LANDS IN ${protectedSeconds}s"
                    hasBalance || refillComplete -> "⚡  TAKE THE SCREEN"
                    else -> "NEXT TAKE IN ${refillSeconds}s"
                },
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = .4.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                offline -> "Reconnect to challenge for ONE."
                isOwner -> "The world is watching your message."
                hasBalance || refillComplete -> "Challenge ${world.reign.owner.handle} to take ONE."
                else -> "Watch an ad or spend credits to skip the wait."
            },
            color = Muted,
            fontFamily = mono,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Filled pips for held takes, an empty pip mid-refill with a live countdown
// next to it - "how many I have" and "when the next one lands" in one glance.
@Composable
private fun TakeBalancePips(balance: Int, cap: Int, refillSeconds: Int, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(cap.coerceAtLeast(1)) { index ->
            if (index > 0) Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (index < balance) accent else Color.White.copy(alpha = .12f))
                    .then(if (index >= balance) Modifier.border(1.dp, accent.copy(alpha = .4f), CircleShape) else Modifier),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            if (balance > 0) "$balance TAKE${if (balance == 1) "" else "S"} HELD" else "NEXT IN ${refillSeconds}s",
            color = Muted,
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = .4.sp,
        )
    }
}

@Composable
private fun LibraryScreen(
    world: WorldState,
    selectedId: String?,
    onCompose: () -> Unit,
    onDeploy: (String) -> Unit,
    onDelete: (String) -> Unit,
    offline: Boolean,
    reconnecting: Boolean,
    onReconnect: () -> Unit,
) {
    OfflineAware(offline, world.lastConnectedAtMillis, reconnecting, onReconnect) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        SectionHeader("YOUR WORDS", "${world.messages.count { it.status == MessageStatus.APPROVED }} APPROVED")
        Spacer(Modifier.height(24.dp))
        Text("YOUR WORDS,\nREADY FOR THE WORLD.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 39.sp, lineHeight = 37.sp, letterSpacing = (-1.4).sp)
        Text("Messages are screened before they can enter the live battle.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 11.dp, bottom = 20.dp))
        PrimaryButton("+  COMPOSE A MESSAGE", Acid, onCompose)
        Spacer(Modifier.height(18.dp))
        world.messages.forEach { message ->
            MessageCard(
                message = message,
                selected = message.id == selectedId,
                onDeploy = { onDeploy(message.id) },
                onDelete = { onDelete(message.id) },
            )
            Spacer(Modifier.height(11.dp))
        }
        Spacer(Modifier.height(6.dp))
        SafetyCard()
    }
    }
}

@Composable
private fun MessageCard(message: OneMessage, selected: Boolean, onDeploy: () -> Unit, onDelete: () -> Unit) {
    val statusColor = when (message.status) {
        MessageStatus.APPROVED -> Acid
        MessageStatus.REVIEWING -> Ice
        MessageStatus.REJECTED -> Orange
        MessageStatus.REVOKED -> Orange
    }
    val canDelete = message.status != MessageStatus.REVIEWING && message.status != MessageStatus.REVOKED
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(InkRaised)
            .border(1.dp, if (selected) statusColor.copy(alpha = .65f) else Color.White.copy(alpha = .08f), RoundedCornerShape(7.dp))
            .padding(17.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(message.status.name, statusColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("USED ${message.timesDeployed}×", color = Muted, fontFamily = mono, fontSize = 12.sp)
                if (canDelete) {
                    Spacer(Modifier.width(12.dp))
                    Text("DELETE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onDelete))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("“${message.text}”", color = if (message.status == MessageStatus.APPROVED) Paper else Muted, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 24.sp)
        if (message.status == MessageStatus.REJECTED && !message.rejectionReason.isNullOrBlank()) {
            Text(message.rejectionReason, color = Orange, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (message.status == MessageStatus.REVOKED) {
            Text("Removed after an upheld report. This message can't be redeployed.", color = Orange, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (message.status == MessageStatus.APPROVED) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier.clickable(onClick = onDeploy),
                    color = statusColor,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("DEPLOY  →", color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

@Composable
private fun HallScreen(world: WorldState, onTakeIt: () -> Unit, offline: Boolean, reconnecting: Boolean, onReconnect: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(HallPeriod.TODAY) }
    var sort by rememberSaveable { mutableStateOf(HallSort.VIEWS) }
    var showFull by rememberSaveable { mutableStateOf(false) }
    val today = world.hallToday.ifEmpty { world.hall }
    val allTime = world.hallAllTime.ifEmpty { world.hall }
    val periodEntries = if (period == HallPeriod.TODAY) today else allTime
    val sortedEntries = remember(periodEntries, sort) { periodEntries.sortedByDescending { it.metricFor(sort) } }
    val visibleEntries = if (showFull) sortedEntries else (sortedEntries.take(5) + sortedEntries.filter { it.owner.id == world.currentUserId }).distinctBy { it.owner.id }
    val selectedPeriodIsLive = world.connected && !world.demoMode && (period == HallPeriod.TODAY || world.hallAllTimeLive)
    // "Today's winning message" is the actual champion (server rank 1), independent
    // of whichever column the viewer is currently sorting by.
    val champion = today.firstOrNull { it.rank == 1 } ?: today.firstOrNull()
    // Compared against whatever row is actually rendered directly above the
    // player, not their true numeric neighbor in sortedEntries - in the
    // collapsed view those can be different rows (the true neighbor might be
    // hidden), and showing a number that doesn't match the row above it reads
    // as broken.
    val currentUserVisibleIndex = visibleEntries.indexOfFirst { it.owner.id == world.currentUserId }
    val rankAbove = visibleEntries.getOrNull(currentUserVisibleIndex - 1)
    val gapToNextRank = if (currentUserVisibleIndex > 0 && rankAbove != null) {
        rankAbove.metricFor(sort) - visibleEntries[currentUserVisibleIndex].metricFor(sort)
    } else null

    OfflineAware(offline, world.lastConnectedAtMillis, reconnecting, onReconnect) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("THE HALL.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 54.sp, lineHeight = 50.sp, letterSpacing = (-2.2).sp)
                Text("RANKED FROM VERIFIED LIVE REIGNS", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp, modifier = Modifier.padding(top = 7.dp))
            }
            StatusPill(if (selectedPeriodIsLive) "LIVE DATA" else if (world.demoMode) "PREVIEW" else "SYNCING", if (selectedPeriodIsLive) Acid else Orange)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            HallPill("TODAY", period == HallPeriod.TODAY, Modifier.weight(1f)) { period = HallPeriod.TODAY; showFull = false }
            Spacer(Modifier.width(8.dp))
            HallPill("ALL TIME", period == HallPeriod.ALL_TIME, Modifier.weight(1f)) { period = HallPeriod.ALL_TIME; showFull = false }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            HallSortChip("◇ LONGEST", sort == HallSort.LONGEST, Modifier.weight(1f)) { sort = HallSort.LONGEST }
            Spacer(Modifier.width(6.dp))
            HallSortChip("TAKEOVERS", sort == HallSort.TAKEOVERS, Modifier.weight(1f)) { sort = HallSort.TAKEOVERS }
            Spacer(Modifier.width(6.dp))
            HallSortChip("VIEWS", sort == HallSort.VIEWS, Modifier.weight(1f)) { sort = HallSort.VIEWS }
        }
        Spacer(Modifier.height(18.dp))
        // The winning message is the headline of the Hall, not a row within
        // it - it sits directly beneath the filters, above rank one, not
        // buried mid-list.
        if (period == HallPeriod.TODAY && champion != null) {
            HallChampionCard(champion)
            Spacer(Modifier.height(18.dp))
        }
        AnimatedContent(period to sort, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "hall-period") {
            Column {
                if (visibleEntries.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("◇", color = Acid, fontSize = 30.sp)
                        Text("THE HALL IS WAITING", color = Paper, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
                        Text("The first verified reign will appear here.", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                } else {
                    visibleEntries.forEachIndexed { index, entry ->
                        val isCurrentUser = entry.owner.id == world.currentUserId
                        if (isCurrentUser) YourRankDivider()
                        HallRow(entry, sort, isCurrentUser, emphasized = !isCurrentUser && index < 3)
                        Spacer(Modifier.height(if (isCurrentUser) 12.dp else 4.dp))
                        // Rendered immediately after the player's own row, not once
                        // after the whole list - in the expanded view those are two
                        // different positions, and a gap card sitting under some
                        // other, unrelated row reads as broken.
                        if (isCurrentUser && gapToNextRank != null && gapToNextRank > 0 && rankAbove != null) {
                            HallGapCard(gapToNextRank, rankAbove.owner.handle, sort, onTakeIt)
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HallStat(world.takeoversToday.toString(), "TAKEOVERS TODAY")
            HallStat(today.size.toString(), "PLAYERS TODAY")
            HallStat(formatNumber(today.sumOf { it.verifiedViews }), "VIEWS TODAY")
        }
        Spacer(Modifier.height(18.dp))
        if (showFull) {
            // A minor control once expanded - demoted to a quiet text link so
            // it doesn't compete with VIEW FULL LEADERBOARD's emphasis.
            Text(
                "COLLAPSE  ↑",
                color = Muted,
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { showFull = false }.padding(vertical = 14.dp),
            )
        } else {
            Surface(color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Acid.copy(alpha = .75f)), modifier = Modifier.fillMaxWidth().clickable { showFull = true }) {
                Text("VIEW FULL LEADERBOARD  ↓", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
        }
        Text("◇ ${world.takeoversToday} TAKEOVERS TODAY // ${if (selectedPeriodIsLive) "UPDATED LIVE" else "SERVER UPDATE REQUIRED"}", color = Muted, fontFamily = mono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
    }
    }
}

private enum class HallPeriod { TODAY, ALL_TIME }
private enum class HallSort { LONGEST, TAKEOVERS, VIEWS }

private fun HallEntry.metricFor(sort: HallSort): Int = when (sort) {
    HallSort.LONGEST -> reignSeconds
    HallSort.TAKEOVERS -> takeovers ?: 0
    HallSort.VIEWS -> verifiedViews
}

private fun HallEntry.metricLabelFor(sort: HallSort): String = when (sort) {
    HallSort.LONGEST -> formatDuration(reignSeconds.toLong())
    HallSort.TAKEOVERS -> takeovers?.toString() ?: "—"
    HallSort.VIEWS -> formatNumber(verifiedViews)
}

private fun formatMetricGap(gap: Int, sort: HallSort): String = when (sort) {
    HallSort.LONGEST -> "${formatDuration(gap.toLong())} MORE"
    HallSort.TAKEOVERS -> "$gap MORE TAKEOVER${if (gap == 1) "" else "S"}"
    HallSort.VIEWS -> "${formatNumber(gap)} MORE VIEWS"
}

@Composable
private fun HallPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(43.dp)
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, if (selected) Acid else Color.White.copy(alpha = .18f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun HallSortChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (selected) Acid else Color.White.copy(alpha = .14f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = .5.sp)
    }
}

@Composable
private fun HallChampionCard(entry: HallEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Acid.copy(alpha = .14f))
            .border(1.dp, Acid.copy(alpha = .55f), RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Acid))
            Spacer(Modifier.width(7.dp))
            Text("TODAY'S WINNING MESSAGE", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))
        AutoFitMessageText("“${entry.message}”", Paper, listOf(17.sp, 15.sp, 13.sp, 12.sp), Modifier.fillMaxWidth())
        Text("— ${entry.owner.handle}", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("HELD FOR", color = Muted, fontFamily = mono, fontSize = 10.sp, letterSpacing = .8.sp)
                Text(formatDuration(entry.reignSeconds.toLong()), color = Paper, fontWeight = FontWeight.Black, fontFamily = mono, fontSize = 15.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatNumber(entry.verifiedViews), color = Paper, fontWeight = FontWeight.Black, fontFamily = mono, fontSize = 15.sp)
                Text(if (entry.verifiedViews == 1) "PERSON" else "PEOPLE", color = Muted, fontFamily = mono, fontSize = 10.sp, letterSpacing = .8.sp)
            }
        }
    }
}

@Composable
private fun HallGapCard(gap: Int, chasingHandle: String, sort: HallSort, onTakeIt: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = .05f))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("CHASING $chasingHandle", color = Muted, fontFamily = mono, fontSize = 10.sp, letterSpacing = .8.sp)
            Text(formatMetricGap(gap, sort), color = Paper, fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Surface(color = Acid, shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable(onClick = onTakeIt)) {
            Text("TAKE IT  →", color = Ink, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun HallStat(value: String, label: String) {
    Column {
        Text(value, color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 24.sp, letterSpacing = (-.8).sp)
        Text(label, color = Muted, fontFamily = mono, fontSize = 10.sp, letterSpacing = .6.sp, modifier = Modifier.padding(top = 2.dp))
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
    onIdentityBackup: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onFeedback: () -> Unit,
    onUnblockAll: () -> Unit,
    onUnblockOne: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    onHowItWorks: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val user = world.currentUser
    val handle = user?.handle ?: if (world.demoMode) "@BOWEI" else "@CONNECTING"
    val initials = user?.initials ?: if (world.demoMode) "BW" else "--"
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 112.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("YOU.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 54.sp, lineHeight = 50.sp, letterSpacing = (-2.2).sp)
            CircleAction("ⓘ", onHowItWorks)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProfilePhoto(user, Acid, Modifier.size(72.dp).clickable(onClick = onEditProfile))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (world.connected) user?.locationLabel() ?: "Location not shared" else "CONNECTING", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .7.sp)
            }
            Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = .16f)))
            Column(Modifier.padding(start = 16.dp).clickable(onClick = onVault), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(world.credits.toString(), color = Acid, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 25.sp)
                Text("ONE CREDITS", color = Muted, fontFamily = mono, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("YOUR STATS", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp)
        Spacer(Modifier.height(8.dp))
        UserStatsPanel(world)
        Spacer(Modifier.height(14.dp))
        SettingsCard("PHOTO & COUNTRY", "Add a public photo and choose your location.", "EDIT", Acid, onEditProfile)
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
            detail = "A push the second someone takes the screen from you." +
                if (ui.pushBlocked) "\nBlocked in Android settings — tap to fix." else "",
            badge = when {
                ui.pushBlocked -> "BLOCKED"
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
            detail = "Link Google to keep your handle and history across devices.",
            badge = "SAVE",
            accent = Acid,
            onClick = onIdentityBackup,
        )
        Spacer(Modifier.height(10.dp))
        SettingsCard(
            title = "ONE CREDITS",
            detail = if (revenueCatReady) "Credit packs are connected and server verified." else "Add the public SDK key to activate credit packs.",
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
            title = "HOW TO PLAY",
            detail = "Watch, take the screen, publish, and build your reign.",
            badge = "START",
            accent = Acid,
            onClick = onHowItWorks,
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
            BlockedAccountsCard(world.blockedAccounts, onUnblockOne, onUnblockAll)
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
        Text(if (world.connected) "GLOBAL LEDGER CONNECTED" else "GLOBAL LEDGER OFFLINE", color = if (world.connected) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.2.sp)
        Text("Ownership, cooldowns, credits, views and race ordering are controlled by the server.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun ProfileOverlay(world: WorldState, ui: OneUiState, onClose: () -> Unit, onSave: (String, String) -> Unit, onPhoto: (android.net.Uri?) -> Unit) {
    val user = world.currentUser
    var country by rememberSaveable(user?.id) { mutableStateOf(user?.countryCode?.takeIf { it in java.util.Locale.getISOCountries() }.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPhoto(uri) }
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        OverlayHeader("YOUR PROFILE", "PHOTO & LOCATION", onClose)
        Spacer(Modifier.height(28.dp))
        ProfilePhoto(user, Acid, Modifier.size(100.dp).align(Alignment.CenterHorizontally))
        Text("Your photo and optional country are public. Your city is never shown. Choose a photo you have permission to use.", color = Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 20.dp))
        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !ui.profileBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink)) {
            Text(if (ui.profileBusy) "SAVING…" else "CHOOSE PROFILE PHOTO", fontWeight = FontWeight.Black)
        }
        if (user?.photoVersion != null) Button(onClick = { onPhoto(null) }, enabled = !ui.profileBusy, modifier = Modifier.fillMaxWidth()) { Text("REMOVE PHOTO") }
        Spacer(Modifier.height(24.dp))
        CountryPicker(country) { country = it }
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onSave("", country) }, enabled = !ui.profileBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink)) { Text("SAVE COUNTRY", fontWeight = FontWeight.Black) }
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
    onWatchAd: () -> Unit,
    onAdOfferShown: () -> Unit,
    onBypassWithCredits: () -> Unit,
) {
    val accent = Acid
    val hasBalance = world.takeBalance > 0
    val adBypassReady = BuildConfig.ADMOB_REWARDED_UNIT_ID.isNotBlank() && world.takeAdBypassesRemainingToday > 0
    val canBypassWithCredits = world.credits >= world.takeBypassCreditCost
    val refillSeconds = rememberCountdownSeconds(world.takeRefillSeconds)
    // Same fix as AuctionCard: a completed refill is only ever collected
    // inside take_one() itself, so the countdown reaching zero must count
    // as takeable too, or the balance can never leave zero.
    val refillComplete = !hasBalance && refillSeconds <= 0
    val adOfferVisible = adBypassReady && !hasBalance && !refillComplete
    LaunchedEffect(adOfferVisible) { if (adOfferVisible) onAdOfferShown() }
    val canAttempt = hasBalance || refillComplete || canBypassWithCredits || adBypassReady
    val selected = world.messages.firstOrNull { it.id == ui.selectedMessageId }
    val busy = ui.challengePhase !in listOf(ChallengePhase.IDLE, ChallengePhase.FAILED)

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
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
                Text("CURRENT OWNER", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhoto(world.reign.owner, accent, Modifier.size(44.dp))
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(world.reign.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(world.reign.owner.locationLabel(), color = Muted, fontFamily = mono, fontSize = 12.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 15.dp).height(1.dp).background(Color.White.copy(alpha = .13f)))
                Text("THE MESSAGE", color = Muted, fontFamily = mono, fontSize = 12.sp)
                Text(world.reign.message.text, color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 25.sp, lineHeight = 25.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text("Choose your move.", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp, bottom = 14.dp))

                ChallengeChoice(
                    "ϟ",
                    "TAKE BALANCE",
                    if (hasBalance) "${world.takeBalance} of ${world.takeBalanceCap} held. Never expires." else if (refillComplete) "Ready now." else "Next take in ${refillSeconds}s.",
                    if (hasBalance) "${world.takeBalance} READY" else if (refillComplete) "READY" else "${refillSeconds}s",
                    Acid,
                    selected = hasBalance || refillComplete,
                )
                if (!hasBalance && !refillComplete) {
                    Spacer(Modifier.height(10.dp))
                    ChallengeChoice(
                        "ϟ",
                        "SPEND ${world.takeBypassCreditCost} CREDIT${if (world.takeBypassCreditCost == 1) "" else "S"}",
                        "Get a take right now. ${world.credits} credits held.",
                        if (canBypassWithCredits) "SPEND" else "GET CREDITS",
                        Ice,
                        selected = false,
                        onClick = if (canBypassWithCredits) onBypassWithCredits else onOpenVault,
                    )
                    Spacer(Modifier.height(10.dp))
                    ChallengeChoice(
                        "▶",
                        "WATCH AN AD",
                        "Get a take right now. ${world.takeAdBypassesRemainingToday} left today.",
                        if (adBypassReady) "WATCH" else "DAILY LIMIT",
                        Orange,
                        selected = false,
                        onClick = if (adBypassReady) onWatchAd else null,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChallengeChoice("LIVE", "LIVE CHALLENGE", "Hold to submit an atomic server-verified takeover.", "ATOMIC", Orange, selected = false)
                Spacer(Modifier.height(16.dp))
                Text("YOUR APPROVED MESSAGE", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .7.sp)
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
                                PrimaryButton("GET ONE CREDITS", Ice, onOpenVault)
                            } else {
                                HoldToOwnButton(
                                    text = if (hasBalance || refillComplete) "HOLD TO CONTINUE — FREE" else "BYPASS THE WAIT ABOVE FIRST",
                                    enabled = (hasBalance || refillComplete) && selected != null,
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
    val longest = world.userLongestReign
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(8.dp)).padding(vertical = 15.dp)) {
        ProfileStat("⬡", longest?.let { formatDuration(it.toLong()) } ?: "—", "LONGEST REIGN", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(58.dp).background(Color.White.copy(alpha = .14f)))
        ProfileStat("ϟ", world.userTakeovers?.toString() ?: "—", "TAKEOVERS", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(58.dp).background(Color.White.copy(alpha = .14f)))
        ProfileStat("◉", world.userVerifiedViews?.let(::formatNumber) ?: "—", "VERIFIED VIEWS", Modifier.weight(1f))
    }
}

@Composable
private fun ProfileStat(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = Paper, fontSize = 14.sp)
        Text(value, color = Acid, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.padding(top = 5.dp))
        Text(label, color = Muted, fontFamily = mono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
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
        Text(icon, color = if (selected) Ink else color, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 28.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(detail, color = if (selected) Ink.copy(alpha = .68f) else Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(status, color = if (selected) Ink else color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun HowItWorksOverlay(
    enabledOnLaunch: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onStartNow: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        LiveField(Acid, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
            OverlayHeader("THE RULES", "ONE SCREEN // ONE OWNER", onClose)
            Spacer(Modifier.height(30.dp))
            Text("SIMPLE ENOUGH\nTO FEEL DANGEROUS.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 39.sp, lineHeight = 36.sp, letterSpacing = (-1.5).sp)
            Text("Everything you tap in ONE changes the same live world for everyone.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 24.dp))
            RuleCard("01", "◉", "WATCH", "There is only one live message. Views count once per verified viewer and reign.", Acid)
            Spacer(Modifier.height(10.dp))
            RuleCard("02", "ϟ", "TAKE", "Choose an approved message and hold to challenge. The server decides the winner atomically.", Orange)
            Spacer(Modifier.height(10.dp))
            RuleCard("03", "♛", "DEFEND", "Your reign lasts until somebody takes it. Every second and verified view enters The Hall.", Ice)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(InkRaised)
                    .border(1.dp, Acid.copy(alpha = .42f), RoundedCornerShape(7.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("START WITH THE GUIDE", color = Paper, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("When this is on, every new app launch begins with onboarding and the spotlight walkthrough.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Switch(checked = enabledOnLaunch, onCheckedChange = onEnabledChanged)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onStartNow, modifier = Modifier.fillMaxWidth().height(52.dp), border = BorderStroke(1.dp, Acid)) {
                Text("PLAY THE GUIDE NOW", color = Acid, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Acid).padding(18.dp)) {
                Text("NO FAKE NUMBERS.", color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("LIVE DATA IS LABELLED LIVE. When disconnected, ONE says so instead of pretending.", color = Ink.copy(alpha = .68f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
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
            Text(detail, color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun IdentityOverlay(
    ui: OneUiState,
    handle: String,
    onClose: () -> Unit,
    onGoogle: (Boolean) -> Unit,
    onHandleChanged: (String) -> Unit,
    onReclaim: () -> Unit,
) {
    var confirmRestore by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Ink).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        OverlayHeader("KEEP YOUR IDENTITY", "OPTIONAL // KEEP PLAYING", onClose)
        Spacer(Modifier.height(30.dp))
        Text("KEEP $handle", color = Paper, fontFamily = display, fontSize = 40.sp)
        Text("If you haven't linked Google, this identity relies on this installation. Link it to return on another device without losing your reigns.", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(vertical = 20.dp))
        if (ui.identityLinked) Text("✓ Google connected. Your handle is protected. Use this Google account to return on another device.", color = Acid, fontSize = 17.sp, modifier = Modifier.padding(bottom = 16.dp))
        Button(onClick = { onGoogle(false) }, enabled = !ui.identityBusy && !ui.identityLinked && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (ui.identityLinked) "Google connected ✓" else if (ui.identityBusy) "Saving your identity…" else "Continue with Google") }
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) Text("Google connection is awaiting setup. You can keep playing.", color = Muted)
        TextButton(onClick = onClose) { Text("Not now") }
        ui.identityError?.let { ErrorStrip(it) }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { confirmRestore = true }, enabled = !ui.identityBusy && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { Text("Restore a Google-linked account") }
        Text("Old unlinked handle? Only an unused, unprotected shell can be reclaimed automatically. Accounts with history require support and proof of ownership.", color = Muted)
        IdentityInput("OLD HANDLE", "BOWEI", ui.recoveryHandle, onHandleChanged, prefix = "@")
        TextButton(onClick = onReclaim, enabled = !ui.identityBusy && ui.recoveryHandle.length >= AuctionRules.HANDLE_MIN) { Text("Check unused handle") }
    }
    if (confirmRestore) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmRestore = false },
        title = { Text("Switch to your Google account?") },
        text = { Text("This switches away from the current identity. Its history and credits are not merged. Link your current identity first if you want to keep it.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; onGoogle(true) }) { Text("Restore account") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
    )
}

@Composable
private fun IdentityInput(label: String, placeholder: String, value: String, onChange: (String) -> Unit, prefix: String = "") {
    Column {
        Text(label, color = Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
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
    onSubmit: (String) -> Unit,
    onKeepAnonymous: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val currentAlias = world.currentUser?.handle ?: "@PLAYER"
    val handleContext = LocalContext.current
    var country by rememberSaveable { mutableStateOf(world.currentUser?.countryCode?.takeIf { it in java.util.Locale.getISOCountries() } ?: suggestedCountry(handleContext)) }
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
                .safeDrawingPadding()
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
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
            Text("YOUR HANDLE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
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
                Text("@", color = Acid, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 29.sp)
                Box(Modifier.weight(1f).padding(start = 2.dp)) {
                    if (ui.handleText.isEmpty()) {
                        Text("BOWEI", color = Muted.copy(alpha = .45f), fontWeight = FontWeight.Black, fontFamily = display, fontSize = 29.sp)
                    }
                    BasicTextField(
                        value = ui.handleText,
                        onValueChange = onTextChanged,
                        textStyle = TextStyle(color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 29.sp),
                        singleLine = true,
                        cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                Text(remaining.toString(), color = if (remaining < 0) Orange else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            CountryPicker(country) { country = it }
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
                Text("HANDLE ONLY. NEVER YOUR LEGAL NAME.", color = Ice, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("No name or email is public. You still have a private account ID and standard server logs, and abuse gets banned.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSubmit(country) },
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
                    fontSize = 12.sp,
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
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(15.dp))
            Text("3-18 CHARACTERS  //  LETTERS, NUMBERS, UNDERSCORES", color = Muted, fontFamily = mono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        VictoryConfetti(resolved, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OneLogo(Acid, compact = true)
                Text(if (stillOwner) "LIVE OWNERSHIP" else "REIGN COMPLETE", color = Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                CircleAction("×", onClose)
            }
            Spacer(Modifier.height(20.dp))
            VictorySeal(resolved.startedAtMillis)
            Spacer(Modifier.height(18.dp))
            Text(if (stillOwner) "YOU OWN ONE." else "YOUR REIGN\nIS HISTORY.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 46.sp, lineHeight = 43.sp, letterSpacing = (-2.sp))
            Text(if (stillOwner) "You took the screen. It’s yours." else "The screen moved on. Your proof remains.", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            Text("YOUR LIVE MESSAGE", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = .8.sp)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(InkRaised).border(1.dp, Acid.copy(alpha = .65f), RoundedCornerShape(8.dp)).padding(17.dp)) {
                AutoFitMessageText(resolved.message, Paper, listOf(20.sp, 18.sp, 16.sp, 14.sp), Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .34f)).border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(8.dp)).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ReceiptMetric(if (stillOwner) formatDuration(((world.reign.protectedUntilMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) else formatDuration(duration.toLong()), if (stillOwner) "PROTECTION" else "REIGN", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(48.dp).background(Color.White.copy(alpha = .14f)))
                ReceiptMetric(formatNumber(total), "VERIFIED VIEWS", Modifier.weight(1f))
            }
            Spacer(Modifier.height(28.dp))
            PrimaryButton(if (stillOwner) "GO LIVE  ((•))" else "WATCH LIVE", Acid, onClose)
            Spacer(Modifier.height(10.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onShare), color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .18f))) {
                Text("SHARE THE PROOF  ↗", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(15.dp))
            }
        }
    }
}

@Composable
private fun VictorySeal(key: Any) {
    val pop = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = 260f)) }
    Box(
        Modifier.fillMaxWidth().height(180.dp).graphicsLayer {
            scaleX = .5f + pop.value * .5f
            scaleY = .5f + pop.value * .5f
            alpha = pop.value.coerceIn(0f, 1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().border(2.dp, Acid, CircleShape))
            ArcText("YOU OWN ONE", Modifier.fillMaxSize(), radius = 73.dp, color = Acid, fontSizeSp = 11f, bottom = false)
            ArcText("THE WORLD IS WATCHING", Modifier.fillMaxSize(), radius = 73.dp, color = Acid, fontSizeSp = 11f, bottom = true)
            Box(Modifier.size(116.dp).border(1.dp, Acid.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Text("1", color = Acid, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 78.sp, letterSpacing = (-5).sp)
            }
        }
    }
}

/** Draws [text] curved along the top or bottom half of a circle of [radius], like text on a seal. */
@Composable
private fun ArcText(text: String, modifier: Modifier = Modifier, radius: Dp, color: Color, fontSizeSp: Float, bottom: Boolean) {
    val argb = color.toArgb()
    Canvas(modifier) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = argb
            this.textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            textSize = fontSizeSp.sp.toPx()
            letterSpacing = 0.1f
        }
        val r = radius.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val oval = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
        val path = android.graphics.Path().apply {
            if (bottom) addArc(oval, 180f, -180f) else addArc(oval, 180f, 180f)
        }
        val pathLength = (Math.PI * r).toFloat()
        val textWidth = paint.measureText(text)
        val hOffset = ((pathLength - textWidth) / 2f).coerceAtLeast(0f)
        val vOffset = if (bottom) -fontSizeSp.sp.toPx() * 0.35f else fontSizeSp.sp.toPx() * 0.15f
        drawContext.canvas.nativeCanvas.drawTextOnPath(text, path, hOffset, vOffset, paint)
    }
}

private enum class ConfettiShape { RECT, CIRCLE, TRIANGLE }

@Composable
private fun VictoryConfetti(key: Any, modifier: Modifier = Modifier) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(4200, easing = LinearEasing))
    }
    val pieceCount = 160
    Canvas(modifier) {
        val colors = listOf(Acid, Orange, Ice, Paper, Magenta, Cobalt)
        repeat(pieceCount) { index ->
            val t = ((progress.value - (index % 11) * .014f) / .82f).coerceIn(0f, 1f)
            if (t > 0f && t < 1f) {
                val seed = ((index * 97) % 389) / 389f
                val burstSeed = ((index * 53) % 211) / 211f
                val x = size.width * (.5f + (seed - .5f) * (.35f + t * 2.6f)) +
                    kotlin.math.sin(t * 15f + index) * (10 + index % 26).dp.toPx()
                val y = size.height * (.22f - t * (.62f + (index % 6) * .08f) + t * t * 2.05f)
                val fadeIn = (t * 9f).coerceIn(0f, 1f)
                val fadeOut = ((1f - t) * 3.4f).coerceIn(0f, 1f)
                val alpha = minOf(fadeIn, fadeOut)
                val color = colors[index % colors.size].copy(alpha = alpha)
                val spin = index * 27f + t * (520f + burstSeed * 640f)
                withTransform({ rotate(spin, Offset(x, y)) }) {
                    when (ConfettiShape.entries[index % 3]) {
                        ConfettiShape.RECT -> drawRect(
                            color,
                            topLeft = Offset(x, y),
                            size = androidx.compose.ui.geometry.Size((3 + index % 4).dp.toPx(), (8 + index % 7).dp.toPx()),
                        )
                        ConfettiShape.CIRCLE -> drawCircle(color, radius = (3 + index % 3).dp.toPx(), center = Offset(x, y))
                        ConfettiShape.TRIANGLE -> {
                            val s = (7 + index % 5).dp.toPx()
                            drawPath(
                                Path().apply {
                                    moveTo(x, y - s / 2)
                                    lineTo(x + s / 2, y + s / 2)
                                    lineTo(x - s / 2, y + s / 2)
                                    close()
                                },
                                color,
                            )
                        }
                    }
                }
            }
        }
        repeat(28) { index ->
            val t = ((progress.value - index * .01f) / .3f).coerceIn(0f, 1f)
            if (t > 0f && t < 1f) {
                val angle = (index / 28f) * 360f
                val radius = size.minDimension * .1f + t * size.minDimension * .55f
                val cx = size.width / 2f + kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat() * radius
                val cy = size.height * .3f + kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat() * radius
                val alpha = (1f - t).coerceIn(0f, 1f)
                drawCircle(Acid.copy(alpha = alpha * .9f), radius = (2 + t * 3).dp.toPx(), center = Offset(cx, cy))
            }
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("COMPOSE", "PRE-CLEAR BEFORE BIDDING", onClose)
        Spacer(Modifier.height(26.dp))
        Text("SAY ONE THING\nWORTH STEALING.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 42.sp, lineHeight = 40.sp, letterSpacing = (-1.5).sp)
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
                Text("THE WHOLE WORLD WILL SEE…", color = Muted.copy(alpha = .55f), fontWeight = FontWeight.Black, fontFamily = display, fontSize = 27.sp, lineHeight = 30.sp)
            }
            BasicTextField(
                value = ui.composeText,
                onValueChange = onTextChanged,
                textStyle = TextStyle(color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 27.sp, lineHeight = 30.sp),
                modifier = Modifier.fillMaxSize(),
                cursorBrush = Brush.verticalGradient(listOf(Acid, Acid)),
            )
            Text("$remaining", color = if (remaining < 0) Orange else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        ui.composeError?.let {
            Spacer(Modifier.height(10.dp))
            ErrorStrip(it)
        }
        Spacer(Modifier.height(17.dp))
        ScreeningRow("ON-DEVICE PRE-FILTER", ui.composeText.isNotBlank())
        ScreeningRow("NO LINKS, EMAILS OR PHONE NUMBERS", ui.composeError?.contains("looks like") != true)
        ScreeningRow("AUTOMATIC SAFETY CHECK", valid)
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
        Text("Approval happens before the live battle. A takeover never waits for moderation.", color = Muted, fontFamily = mono, textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VaultOverlay(
    world: WorldState,
    revenueCatReady: Boolean,
    onClose: () -> Unit,
    onPurchase: (Int) -> Unit,
    onRestore: () -> Unit,
    onBuyOnWeb: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("‹", if (revenueCatReady) "LIVE STORE" else "STORE OFFLINE", onClose)
        Spacer(Modifier.height(24.dp))
        Text("ONE CREDITS.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 40.sp, letterSpacing = (-1.8).sp)
        Text("Skip your cooldown. Take ONE back now.", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Box(Modifier.fillMaxWidth().padding(vertical = 18.dp).height(1.dp).background(Color.White.copy(alpha = .14f)))
        Text("YOUR BALANCE", color = Muted, fontFamily = mono, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Text("🎟", fontSize = 27.sp)
            Spacer(Modifier.width(12.dp))
            Text("${formatNumber(world.credits)} CREDITS", color = Acid, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }

        CreditPack("ϟ", "SPARK", 3, "£0.99", Acid) { onPurchase(3) }
        Spacer(Modifier.height(11.dp))
        CreditPack("✦", "CHALLENGER", 20, "£4.99", Ice) { onPurchase(20) }
        Spacer(Modifier.height(11.dp))
        CreditPack("♛", "HEADLINER", 50, "£9.99", Orange) { onPurchase(50) }
        Spacer(Modifier.height(22.dp))
        Text("PURCHASES ARE PROCESSED BY GOOGLE PLAY AND VERIFIED SERVER-SIDE BEFORE CREDITS ARE ADDED.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Surface(modifier = Modifier.fillMaxWidth().clickable(enabled = revenueCatReady, onClick = onRestore), color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .16f))) {
            Text("↻  RESTORE GOOGLE PLAY PURCHASES", color = if (revenueCatReady) Paper else Muted, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
        if (BuildConfig.ONE_WEB_FUNNEL_URL.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onBuyOnWeb), color = Color.Transparent, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .16f))) {
                Text("⎋  BUY ON THE WEB", color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
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
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        OverlayHeader("SAFETY", "PUBLIC UGC CONTROL", onClose)
        Spacer(Modifier.height(30.dp))
        Text("REPORT THE\nLIVE MESSAGE.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 40.sp)
        Text("Reports enter the global moderation queue. Blocking immediately hides this person and their future content from you.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 22.dp))
        listOf("HATE OR HARASSMENT", "THREAT OR VIOLENCE", "PERSONAL INFORMATION", "SCAM OR IMPERSONATION", "OTHER").forEach { reason ->
            Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable { onReport(reason) }, color = InkRaised, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .07f))) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(reason, color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("→", color = Orange, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (canBlock) {
            Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onBlock), color = Orange.copy(alpha = .12f), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .4f))) {
                Text("BLOCK $owner", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Safety contact: oneglobalscreen@gmail.com", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("FEEDBACK", "REAL TESTER NOTES", onClose)
        Spacer(Modifier.height(28.dp))
        Text("MAKE ONE\nBETTER.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 40.sp, letterSpacing = (-1.6).sp)
        Text("Your note is attached to your anonymous ONE account so we can investigate without collecting your name or email.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(22.dp))
        Text("WHAT TYPE OF NOTE?", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
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
                    Text(category, color = if (selected) Ink else Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
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
            Text("$remaining", color = if (remaining < 0) Orange else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomEnd))
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
        Text("Please do not include passwords, payment details, or private information.", color = Muted, fontFamily = mono, textAlign = TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
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
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("PRIVACY", "IRREVERSIBLE ACTION", onClose)
        Spacer(Modifier.height(30.dp))
        Text("DELETE YOUR\nONE IDENTITY.", color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 40.sp)
        Text(
            "This permanently deletes your anonymous account, handle, message library, reactions, reports, credits and device session. Past reigns remain only as anonymised @DELETED ledger entries so the global record cannot be rewritten.",
            color = Muted,
            fontFamily = mono,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(24.dp))
        Surface(color = Orange.copy(alpha = .1f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .38f))) {
            Column(Modifier.padding(18.dp)) {
                Text("NO UNDO. NO RECOVERY.", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("ONE will create a completely new anonymous identity after deletion so the app can reopen safely.", color = Paper, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
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
            Text(if (deleting) "DELETING SECURELY..." else "PERMANENTLY DELETE ACCOUNT", fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp), color = InkRaised, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
            Text("OPEN DELETION HELP", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Questions: oneglobalscreen@gmail.com", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// Blocks only the take action, not the app: watching Live/Hall/Words stays
// fully available for a banned or suspended account, matching what the
// server itself enforces (view/watch RPCs never check banned_at/
// suspended_until - only take_one and similar action RPCs do).
private fun remainingSuspensionLabel(untilMillis: Long): String {
    val minutes = ((untilMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "less than a minute"
        minutes < 60L -> "about $minutes minute${if (minutes == 1L) "" else "s"}"
        else -> {
            val hours = minutes / 60
            "about $hours hour${if (hours == 1L) "" else "s"}"
        }
    }
}

@Composable
private fun RestrictedOverlay(
    world: WorldState,
    onClose: () -> Unit,
) {
    val banned = world.accountRestriction == "banned"
    val suspendedUntil = world.accountSuspendedUntilMillis
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        OverlayHeader("ACCOUNT STATUS", if (banned) "PERMANENT" else "TEMPORARY", onClose)
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(64.dp).clip(CircleShape).background(Orange.copy(alpha = .12f)).border(1.dp, Orange.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
            Text("!", color = Orange, fontWeight = FontWeight.Black, fontFamily = mono, fontSize = 26.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (banned) "YOU CAN'T\nTAKE ONE." else "TAKING IS\nPAUSED.",
            color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 43.sp, lineHeight = 40.sp,
        )
        Text(
            if (banned) "Your account was removed from taking the screen for violating ONE's community rules. You can still watch everything live — you just can't take it."
            else "Your account can't take the screen for a little while, for violating ONE's community rules. You can still watch everything live — taking comes back automatically.",
            color = Muted,
            fontFamily = mono,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 14.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (!world.accountRestrictionReason.isNullOrBlank()) {
            Surface(color = InkRaised, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
                Column(Modifier.padding(18.dp)) {
                    Text("REASON", color = Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(world.accountRestrictionReason, color = Paper, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (!banned && suspendedUntil != null) {
            Surface(color = Orange.copy(alpha = .1f), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .38f))) {
                Column(Modifier.padding(18.dp)) {
                    Text("TAKING RETURNS IN", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text(remainingSuspensionLabel(suspendedUntil), color = Paper, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = InkRaised, contentColor = Paper),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("KEEP WATCHING", fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Think this is a mistake? oneglobalscreen@gmail.com", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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
        NavItem(Icons.Filled.EmojiEvents, "CHALLENGES", MainTab.LIBRARY, selected, onSelected)
        NavItem(Icons.Filled.WorkspacePremium, "HALL", MainTab.HALL, selected, onSelected)
        NavItem(Icons.Filled.Person, "YOU", MainTab.YOU, selected, onSelected)
    }
}

@Composable
private fun RowScope.NavItem(icon: String, label: String, tab: MainTab, selected: MainTab, onSelected: (MainTab) -> Unit) {
    val active = tab == selected
    val targets = LocalTourTargets.current
    Column(
        Modifier
            .weight(1f)
            .onGloballyPositioned { targets[tab.name] = it.boundsInRoot() }
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
private fun RowScope.NavItem(icon: ImageVector, label: String, tab: MainTab, selected: MainTab, onSelected: (MainTab) -> Unit) {
    val active = tab == selected
    val targets = LocalTourTargets.current
    Column(
        Modifier
            .weight(1f)
            .onGloballyPositioned { targets[tab.name] = it.boundsInRoot() }
            .fillMaxHeight()
            .clickable { onSelected(tab) }
            .background(Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) Acid else Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
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
        Text("ONE", color = Ink.copy(alpha = alpha.value), fontWeight = FontWeight.Black, fontFamily = display, fontSize = 96.sp, letterSpacing = (-6).sp, modifier = Modifier.scale(scale.value))
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
        Text(status, color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            steps.forEachIndexed { index, _ ->
                Box(Modifier.weight(1f).height(7.dp).background(if (index <= current) Acid else Color.White.copy(alpha = .08f), CircleShape))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("IDEMPOTENCY KEY  ${System.currentTimeMillis().toString().takeLast(8)}", color = Muted, fontFamily = mono, fontSize = 12.sp)
    }
}

@Composable
private fun WonPanel(message: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Acid).padding(19.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YOU TOOK ONE", color = Ink, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 27.sp)
        Text("“$message”", color = Ink.copy(alpha = .72f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.dp))
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
        Text(if (selected) "SELECTED  ◆" else "APPROVED", color = if (selected) Ink.copy(alpha = .6f) else Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun HallRow(entry: HallEntry, sort: HallSort, isCurrentUser: Boolean, emphasized: Boolean) {
    val rankColor = when (entry.rank) { 1 -> Acid; 2 -> Ice; 3 -> Orange; else -> Muted }
    val podium = entry.rank in 1..3
    if (isCurrentUser) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Acid)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (podium) RankCrown(entry.rank, Ink, Modifier.width(50.dp)) else Text(entry.rank.toString(), color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(28.dp))
            ProfilePhoto(entry.owner, Ink, Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.owner.handle, color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp, maxLines = 1)
                Text(entry.owner.locationLabel(), color = Ink.copy(alpha = .7f), fontFamily = mono, fontSize = 11.sp)
            }
            Text(entry.metricLabelFor(sort), color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 15.sp)
            RankMovement(Ink.copy(alpha = .55f))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (emphasized) 10.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (podium) {
            RankCrown(entry.rank, rankColor, Modifier.width(50.dp))
        } else {
            Text(
                entry.rank.toString(),
                color = rankColor,
                fontWeight = FontWeight.Black,
                fontFamily = display,
                fontSize = if (emphasized) 22.sp else 15.sp,
                modifier = Modifier.width(30.dp),
            )
        }
        ProfilePhoto(entry.owner, rankColor, Modifier.size(if (emphasized) 40.dp else 32.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.owner.handle, color = Paper, fontWeight = FontWeight.Black, fontSize = if (emphasized) 13.sp else 12.sp, maxLines = 1)
            Text(entry.owner.locationLabel(), color = Muted, fontFamily = mono, fontSize = 11.sp)
        }
        Text(entry.metricLabelFor(sort), color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = if (emphasized) 15.sp else 13.sp)
        RankMovement(Muted)
    }
}

// Real rank-history (who moved up or down since last time) isn't tracked
// anywhere yet, so this is honestly a placeholder, not a claim of "no
// change" - a blank cell here reads as a rendering bug, so every row gets
// a neutral dash until real movement tracking exists to back real arrows.
@Composable
private fun RankMovement(color: Color) {
    Text("—", color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)
}

@Composable
private fun RankCrown(rank: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("♛", color = color, fontSize = 15.sp)
        Spacer(Modifier.width(3.dp))
        Text("#$rank", color = color, fontWeight = FontWeight.Black, fontFamily = mono, fontSize = 13.sp)
    }
}

@Composable
private fun YourRankDivider() {
    Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Acid.copy(alpha = .55f)))
        Text("YOUR RANK", color = Acid, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Acid.copy(alpha = .55f)))
    }
}

@Composable
private fun WalletHero(credits: Int, onClick: () -> Unit) {
    val targets = LocalTourTargets.current
    Box(Modifier.fillMaxWidth().onGloballyPositioned { targets["WALLET"] = it.boundsInRoot() }.clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(Color(0xFF27272E), Color(0xFF111115)))).border(1.dp, Acid.copy(alpha = .42f), RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(19.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ONE VAULT", color = Muted, fontFamily = mono, fontSize = 12.sp, letterSpacing = 1.1.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(formatNumber(credits), color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 39.sp, letterSpacing = (-1).sp)
                    Text(" CREDITS", color = Acid, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Box(Modifier.size(50.dp).clip(CircleShape).background(Acid), contentAlignment = Alignment.Center) {
                Text("+", color = Ink, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun DailyCapCard(world: WorldState) {
    val cap = world.takeBalanceCap.coerceAtLeast(1)
    val progress = (world.takeBalance.toFloat() / cap).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(7.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = .08f), style = Stroke(5.dp.toPx()))
                drawArc(Acid, -90f, progress.coerceIn(0f, 1f) * 360f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
            }
            Text(if (world.takeBalance > 0) "${world.takeBalance}" else "${world.takeRefillSeconds}s", color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("TAKE BALANCE", color = Paper, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text(if (world.takeBalance > 0) "$cap free takes max. Yours never expire." else "Next take in ${world.takeRefillSeconds}s, or bypass with an ad or credits.", color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, detail: String, badge: String, accent: Color, onClick: () -> Unit) {
    val icon = when {
        title.contains("ALERT") || title.contains("NOTIFICATION") -> "♢"
        title.contains("CREDIT") -> "ϟ"
        title.contains("PRIVACY") -> "⬡"
        title.contains("INVITE") -> "◎"
        title.contains("SUPPORT") || title.contains("FEEDBACK") -> "?"
        title.contains("DELETE") -> "×"
        else -> "●"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 90.dp).clip(RoundedCornerShape(7.dp)).background(InkRaised).border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(7.dp)).clickable(onClick = onClick).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, accent.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, color = accent, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(detail, color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(badge, color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = if (badge == "›") 22.sp else 8.sp)
    }
}

@Composable
private fun BlockedAccountsCard(
    accounts: List<com.oneglobal.billboard.model.BlockedAccount>,
    onUnblockOne: (String) -> Unit,
    onUnblockAll: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(InkRaised)
            .border(1.dp, Color.White.copy(alpha = .11f), RoundedCornerShape(7.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 90.dp).clickable { expanded = !expanded }.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, Orange.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
                Text("●", color = Orange, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("BLOCKED ACCOUNTS", color = Paper, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("${accounts.size} account(s) hidden from your live screen, Hall and activity.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Text(if (expanded) "▲" else "▼", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
            accounts.forEach { account ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(account.handle, color = Paper, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        modifier = Modifier.clickable { onUnblockOne(account.id) },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Orange.copy(alpha = .5f)),
                    ) {
                        Text("UNBLOCK", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .08f)))
            Text(
                "UNBLOCK ALL",
                color = Muted,
                fontFamily = mono,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onUnblockAll).padding(14.dp),
            )
        }
    }
}

@Composable
private fun CreditPack(icon: String, name: String, amount: Int, price: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(104.dp).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, color = Ink, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 34.sp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("${formatNumber(amount)} CREDITS", color = Ink.copy(alpha = .76f), fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Canvas(Modifier.width(1.dp).height(72.dp)) {
                val dash = 5.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(Ink.copy(alpha = .34f), Offset(0f, y), Offset(0f, (y + dash).coerceAtMost(size.height)), strokeWidth = 1.dp.toPx())
                    y += dash * 1.8f
                }
            }
            Text(price, color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.padding(start = 18.dp))
        }
        Box(Modifier.size(20.dp).align(Alignment.CenterStart).offset(x = (-10).dp).background(Ink, CircleShape))
        Box(Modifier.size(20.dp).align(Alignment.CenterEnd).offset(x = 10.dp).background(Ink, CircleShape))
    }
}

@Composable
private fun RuleStrip(vararg rules: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        rules.forEach { rule -> Text("• ${rule.uppercase()}", color = Muted, fontFamily = mono, fontSize = 12.sp) }
    }
}

@Composable
private fun SafetyCard() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Orange.copy(alpha = .08f)).border(1.dp, Orange.copy(alpha = .23f), RoundedCornerShape(18.dp)).padding(15.dp)) {
        Text("SAFETY IS PART OF THE PRODUCT", color = Orange, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text("On-device pre-filter + server review + reporting + account bans + emergency global removal.", color = Muted, fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ScreeningRow(text: String, passed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, color = if (passed) Paper else Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(if (passed) "PASS  ◆" else "WAIT", color = if (passed) Acid else Muted, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.height(112.dp).clip(RoundedCornerShape(19.dp)).background(InkRaised).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontFamily = mono, fontSize = 12.sp)
        Text(value, color = Paper, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 29.sp)
    }
}

@Composable
private fun ErrorStrip(text: String) {
    Surface(color = Orange.copy(alpha = .12f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Orange.copy(alpha = .35f))) {
        Text(text, color = Orange, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(11.dp))
    }
}

@Composable
private fun PushPermissionPrompt(onAccept: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("YOU OWN THE SCREEN.", fontFamily = display, fontWeight = FontWeight.Black) },
        text = { Text("Want to know the second someone takes it?") },
        confirmButton = { TextButton(onClick = onAccept) { Text("Yes, alert me") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

// Minutes elapsed since [sinceMillis], ticking live once a second. Shared by
// every offline-state element that needs to say "X minutes ago" in sync.
@Composable
private fun rememberElapsedMinutes(sinceMillis: Long): Int {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return ((now - sinceMillis) / 60_000L).toInt().coerceAtLeast(0)
}

@Composable
private fun OfflineScrim(modifier: Modifier = Modifier) {
    // Nothing under the scrim should be reachable while offline - a click
    // handler with no visual feedback swallows every touch that would
    // otherwise fall through to the (still-rendered) live content beneath it.
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .74f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    )
}

@Composable
private fun OfflineStalledBar(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth().height(3.dp),
        color = Orange,
        trackColor = Orange.copy(alpha = .16f),
    )
}

@Composable
private fun LastSeenTag(lastConnectedAtMillis: Long, modifier: Modifier = Modifier) {
    val minutes = rememberElapsedMinutes(lastConnectedAtMillis)
    Surface(
        color = Orange.copy(alpha = .16f),
        border = BorderStroke(1.dp, Orange.copy(alpha = .5f)),
        shape = RoundedCornerShape(5.dp),
        modifier = modifier,
    ) {
        Text(
            if (minutes <= 0) "LAST SEEN JUST NOW" else "LAST SEEN $minutes MINUTE${if (minutes == 1) "" else "S"} AGO",
            color = Orange,
            fontFamily = mono,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            letterSpacing = .5.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun WaitingForConnectionPill(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = .55f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = Muted, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(8.dp))
        Text("WAITING FOR CONNECTION…", color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = .4.sp)
    }
}

// The bottom stack in every offline-state variant: a small waiting pill, then
// the dark card - amber border, a dot + heading, the "last thing you saw"
// body copy, and a full-width RECONNECT button that swaps its label for a
// spinner while retrying.
@Composable
private fun OfflineCard(lastConnectedAtMillis: Long, reconnecting: Boolean, onReconnect: () -> Unit, modifier: Modifier = Modifier) {
    val minutes = rememberElapsedMinutes(lastConnectedAtMillis)
    val minutesLabel = if (minutes <= 0) "moments" else "$minutes minute${if (minutes == 1) "" else "s"}"
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InkRaised)
            .border(1.dp, Orange, RoundedCornerShape(10.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Orange))
            Spacer(Modifier.width(8.dp))
            Text("You've gone offline", color = Orange, fontWeight = FontWeight.Black, fontFamily = display, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "This is the last thing you saw, $minutesLabel ago. It may already belong to someone else.",
            color = Muted,
            fontFamily = mono,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onReconnect,
            enabled = !reconnecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Orange,
                contentColor = Ink,
                disabledContainerColor = Orange.copy(alpha = .6f),
                disabledContentColor = Ink.copy(alpha = .8f),
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (reconnecting) {
                CircularProgressIndicator(color = Ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("RECONNECT", fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = .5.sp)
            }
        }
    }
}

// Wraps a whole screen's content with the offline treatment: content stays
// rendered underneath (never swapped out for a placeholder - it's the last
// real thing the viewer saw), a scrim dims it, a stalled bar sits at the very
// top, and the reconnect card floats above the tab bar. Screens that need
// more (Live's last-seen tag, disabled take button, em-dash stats) add those
// inline themselves; this only owns the parts every screen shares.
@Composable
private fun OfflineAware(
    offline: Boolean,
    lastConnectedAtMillis: Long,
    reconnecting: Boolean,
    onReconnect: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        if (offline) {
            OfflineScrim(Modifier.matchParentSize())
            OfflineStalledBar(Modifier.align(Alignment.TopCenter))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
            ) {
                WaitingForConnectionPill()
                Spacer(Modifier.height(10.dp))
                OfflineCard(
                    lastConnectedAtMillis = lastConnectedAtMillis,
                    reconnecting = reconnecting,
                    onReconnect = onReconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RevengeBanner(world: WorldState, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val owner = world.reign.owner
    BoxWithConstraints(Modifier.fillMaxSize().background(Orange)) {
        DethronedTexture(Modifier.fillMaxSize())
        val compact = maxHeight < 720.dp
        val headlineSize = if (compact) 61.sp else 72.sp
        val headlineLine = if (compact) 57.sp else 67.sp
        Column(
            Modifier.fillMaxSize().safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CrownMark(Modifier.size(if (compact) 21.dp else 25.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("DETHRONED", color = Ink, fontFamily = display, fontSize = if (compact) 14.sp else 16.sp, letterSpacing = .2.sp)
                }
                CircleActionDark("!", onOpen)
            }

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
            Text("THEY", color = Ink, fontFamily = display, fontSize = headlineSize, lineHeight = headlineLine, letterSpacing = (-2.4).sp)
            Text("TOOK", color = Ink, fontFamily = display, fontSize = headlineSize, lineHeight = headlineLine, letterSpacing = (-2.4).sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("ONE", color = Paper, fontFamily = display, fontSize = headlineSize, lineHeight = headlineLine, letterSpacing = (-2.4).sp)
                Text(".", color = Acid, fontFamily = display, fontSize = headlineSize, lineHeight = headlineLine)
            }

            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = .7f)))
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(owner, Acid, Modifier.size(if (compact) 54.dp else 62.dp))
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(owner.handle, color = Ink, fontFamily = display, fontSize = if (compact) 24.sp else 28.sp, maxLines = 1)
                    Text("TOOK THE SCREEN.", color = Ink.copy(alpha = .72f), fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("♛  ${owner.handle} IS NOW THE OWNER.", color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .45.sp)

            Spacer(Modifier.weight(1f))
            Text("YOUR PREVIOUS REIGN", color = Ink.copy(alpha = .72f), fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Ink)
                    .border(1.dp, Paper.copy(alpha = .14f), RoundedCornerShape(6.dp))
                    .padding(vertical = if (compact) 10.dp else 13.dp),
            ) {
                ProfileStat("⬡", world.userLongestReign?.let { formatDuration(it.toLong()) } ?: "—", "LONGEST REIGN", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(50.dp).background(Paper.copy(alpha = .2f)))
                ProfileStat("ϟ", world.userTakeovers?.toString() ?: "—", "TAKEOVERS", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(50.dp).background(Paper.copy(alpha = .2f)))
                ProfileStat("◉", world.userVerifiedViews?.let(::formatNumber) ?: "—", "VERIFIED VIEWS", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Ink), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth().height(if (compact) 54.dp else 58.dp)) {
                Text("ϟ  TAKE IT BACK", fontFamily = display, fontSize = 19.sp, letterSpacing = .3.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper), shape = RoundedCornerShape(5.dp), modifier = Modifier.fillMaxWidth().height(if (compact) 50.dp else 54.dp)) {
                Text("◉  WATCH LIVE", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CrownMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val crown = Path().apply {
            moveTo(size.width * .08f, size.height * .28f)
            lineTo(size.width * .3f, size.height * .53f)
            lineTo(size.width * .5f, size.height * .17f)
            lineTo(size.width * .7f, size.height * .53f)
            lineTo(size.width * .92f, size.height * .28f)
            lineTo(size.width * .82f, size.height * .82f)
            lineTo(size.width * .18f, size.height * .82f)
            close()
        }
        drawPath(crown, Ink)
        drawRect(Ink, topLeft = Offset(size.width * .16f, size.height * .86f), size = Size(size.width * .68f, size.height * .1f))
    }
}

@Composable
private fun DethronedTexture(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        repeat(90) { index ->
            val x = ((index * 83) % 101) / 101f * size.width
            val y = ((index * 47) % 97) / 97f * size.height
            drawCircle(Ink.copy(alpha = .045f), radius = (1 + index % 3).dp.toPx(), center = Offset(x, y))
        }
        repeat(8) { index ->
            val y = size.height * (.08f + index * .13f)
            drawLine(Paper.copy(alpha = .025f), Offset(0f, y), Offset(size.width, y + 13.dp.toPx()), 1.dp.toPx())
        }
    }
}

@Composable
private fun ToastBar(text: String) {
    Surface(color = Paper, shape = RoundedCornerShape(14.dp), shadowElevation = 10.dp) {
        Text(text, color = Ink, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
    }
}

@Composable
private fun SectionHeader(left: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Acid))
            Spacer(Modifier.width(7.dp))
            Text(left, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Text(right, color = Muted, fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun OverlayHeader(left: String, right: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(left, color = Paper, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
            Text(right, color = Muted, fontFamily = mono, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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
        Text(initials, color = accent, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
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
        Text(text, color = Paper, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun CircleAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .08f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = Paper, modifier = Modifier.size(18.dp))
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
        Text(formatNumber(value), color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Text(label, color = Muted, fontFamily = mono, fontSize = 12.sp)
    }
}

@Composable
private fun ReceiptMetric(value: String, label: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = if (dark) Ink else Paper, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text(label, color = if (dark) Ink.copy(alpha = .62f) else Muted, fontFamily = mono, fontSize = 12.sp)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = .13f), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, color.copy(alpha = .35f))) {
        Text(text, color = color, fontFamily = mono, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .6.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun PrimaryButton(text: String, color: Color, onClick: () -> Unit, foreground: Color = Ink) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = foreground), shape = RoundedCornerShape(6.dp)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = .3.sp)
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
