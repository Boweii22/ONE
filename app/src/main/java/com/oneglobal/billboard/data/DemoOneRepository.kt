package com.oneglobal.billboard.data

import com.oneglobal.billboard.model.ChallengePhase
import com.oneglobal.billboard.model.ChallengeResult
import com.oneglobal.billboard.model.HallEntry
import com.oneglobal.billboard.model.MessageStatus
import com.oneglobal.billboard.model.OneEvent
import com.oneglobal.billboard.model.OneMessage
import com.oneglobal.billboard.model.OneOwner
import com.oneglobal.billboard.model.PaletteKey
import com.oneglobal.billboard.model.Reign
import com.oneglobal.billboard.model.ReignReceipt
import com.oneglobal.billboard.model.WorldState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.random.Random

class DemoOneRepository : OneRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val auctionMutex = Mutex()
    private var tickerJob: Job? = null
    private var rivalJob: Job? = null
    private val random = Random(0x0A11CE)

    private var user = OneOwner(
        id = "user_bowei",
        handle = "@BOWEI",
        city = "LONDON",
        countryCode = "GB",
        verified = true,
        initials = "BW",
    )

    private val aura = OneOwner(
        id = "owner_aura",
        handle = "@AURA",
        city = "TOKYO",
        countryCode = "JP",
        verified = true,
        initials = "AU",
    )

    private val nova = OneOwner(
        id = "owner_nova",
        handle = "@NOVA",
        city = "LAGOS",
        countryCode = "NG",
        verified = true,
        initials = "NV",
    )

    private val _events = MutableSharedFlow<OneEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<OneEvent> = _events.asSharedFlow()

    private val initialNow = System.currentTimeMillis()
    private val initialMessages = listOf(
        OneMessage(
            id = "msg_world",
            text = "THE INTERNET IS WATCHING ITSELF.",
            status = MessageStatus.APPROVED,
            createdAtMillis = initialNow - 86_400_000L,
            timesDeployed = 2,
        ),
        OneMessage(
            id = "msg_mum",
            text = "MUM, I MADE IT TO THE WHOLE WORLD.",
            status = MessageStatus.APPROVED,
            createdAtMillis = initialNow - 43_200_000L,
        ),
        OneMessage(
            id = "msg_human",
            text = "NOT AN AD. JUST A HUMAN SAYING HELLO.",
            status = MessageStatus.APPROVED,
            createdAtMillis = initialNow - 21_600_000L,
        ),
    )

    private val initialReign = Reign(
        id = "reign_aura",
        owner = aura,
        message = OneMessage(
            id = "live_aura",
            text = "IF YOU'RE READING THIS, I OWN A PIECE OF YOUR ATTENTION.",
            status = MessageStatus.APPROVED,
            createdAtMillis = initialNow - 280_000L,
        ),
        startedAtMillis = initialNow - 96_000L,
        protectedUntilMillis = initialNow - 1_000L,
        paidCredits = 390,
        openingPrice = 520,
        floorPrice = 90,
        palette = PaletteKey.COBALT,
    )

    private val _world = MutableStateFlow(
        WorldState(
            reign = initialReign,
            currentPrice = 0,
            appViews = 12_482,
            webViews = 31_905,
            liveWatchers = 2_184,
            credits = 3,
            userDailyReignSeconds = 86,
            userRetakesToday = 0,
            dailyCapSeconds = AuctionRules.DAILY_REIGN_CAP_SECONDS,
            messages = initialMessages,
            hall = seededHall(),
            hallToday = seededHall(),
            hallAllTime = seededHall().mapIndexed { index, entry ->
                entry.copy(rank = index + 1, reignSeconds = entry.reignSeconds * 4, verifiedViews = entry.verifiedViews * 3)
            },
            history = emptyList(),
            takeoversToday = 1,
            currentUser = user,
        ),
    )
    override val world: StateFlow<WorldState> = _world.asStateFlow()

    override fun start() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(700)
                val now = System.currentTimeMillis()
                _world.update { current ->
                    val appGain = random.nextInt(3, 12)
                    val webGain = random.nextInt(14, 52)
                    val watcherDrift = random.nextInt(-8, 14)
                    current.copy(
                        currentPrice = 0,
                        appViews = current.appViews + appGain,
                        webViews = current.webViews + webGain,
                        liveWatchers = (current.liveWatchers + watcherDrift).coerceAtLeast(900),
                        cooldownRemainingSeconds = (current.cooldownRemainingSeconds - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    override fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override suspend fun challenge(
        messageId: String,
        onPhase: (ChallengePhase, String) -> Unit,
    ): ChallengeResult = auctionMutex.withLock {
        val before = _world.value
        val selected = before.messages.firstOrNull {
            it.id == messageId && it.status == MessageStatus.APPROVED
        } ?: return@withLock ChallengeResult.Failure("Choose an approved message first.")

        val now = System.currentTimeMillis()
        if (now < before.reign.protectedUntilMillis) {
            val seconds = ((before.reign.protectedUntilMillis - now + 999L) / 1_000L).toInt()
            return@withLock ChallengeResult.Failure("This reign is protected for ${seconds}s.")
        }
        if (AuctionRules.remainingDailySeconds(before.userDailyReignSeconds) <= 0) {
            return@withLock ChallengeResult.Failure("Your daily reign cap has been reached.")
        }

        val cost = if (before.cooldownRemainingSeconds > 0) 1 else 0
        if (before.credits < cost) {
            return@withLock ChallengeResult.Failure("You need ${cost - before.credits} more ONE credits.")
        }

        onPhase(ChallengePhase.RESERVING, "RESERVING THE ONLY SCREEN")
        delay(620)
        onPhase(ChallengePhase.VERIFYING, "VERIFYING MESSAGE APPROVAL")
        delay(540)
        onPhase(ChallengePhase.SPENDING, if (cost == 0) "FREE TAKEOVER" else "SPENDING 1 REVENGE TICKET")
        delay(700)
        onPhase(ChallengePhase.COMMITTING, "COMMITTING GLOBAL OWNERSHIP")
        delay(620)

        // Re-read under the mutex. In production this maps to a server-side
        // reservation + idempotent currency-spend saga.
        val latest = _world.value
        if (latest.reign.id != before.reign.id) {
            return@withLock ChallengeResult.Failure("Someone else reached ONE first. You were not charged.")
        }

        val takeoverAt = System.currentTimeMillis()
        val receipt = ReignReceipt(
            owner = user,
            message = selected.text,
            paidCredits = cost,
            startedAtMillis = takeoverAt,
            appViews = 0,
            webViews = 0,
            previousOwner = before.reign.owner.handle,
        )
        val newReign = Reign(
            id = UUID.randomUUID().toString(),
            owner = user,
            message = selected.copy(timesDeployed = selected.timesDeployed + 1),
            startedAtMillis = takeoverAt,
            protectedUntilMillis = takeoverAt + AuctionRules.PROTECTED_SECONDS * 1_000L,
            paidCredits = cost,
            openingPrice = 0,
            floorPrice = 0,
            palette = PaletteKey.ACID,
        )
        _world.update { current ->
            current.copy(
                reign = newReign,
                currentPrice = newReign.openingPrice,
                appViews = 0,
                webViews = 0,
                liveWatchers = current.liveWatchers + 240,
                credits = current.credits - cost,
                userRetakesToday = current.userRetakesToday + 1,
                cooldownRemainingSeconds = 30,
                takeoversToday = current.takeoversToday + 1,
                messages = current.messages.map {
                    if (it.id == selected.id) it.copy(timesDeployed = it.timesDeployed + 1) else it
                },
                history = listOf(receipt) + current.history,
            )
        }
        _events.tryEmit(OneEvent.TakeoverWon(receipt))
        scheduleRivalTakeover(newReign.id)
        ChallengeResult.Success(receipt)
    }

    private fun scheduleRivalTakeover(expectedReignId: String) {
        rivalJob?.cancel()
        rivalJob = scope.launch {
            delay(18_000)
            auctionMutex.withLock {
                val current = _world.value
                if (current.reign.id != expectedReignId || current.reign.owner.id != user.id) return@withLock

                val now = System.currentTimeMillis()
                val completed = ReignReceipt(
                    owner = user,
                    message = current.reign.message.text,
                    paidCredits = current.reign.paidCredits,
                    startedAtMillis = current.reign.startedAtMillis,
                    endedAtMillis = now,
                    appViews = current.appViews,
                    webViews = current.webViews,
                    previousOwner = aura.handle,
                    dethronedBy = nova.handle,
                )
                val rivalMessage = OneMessage(
                    id = "rival_${now}",
                    text = "YOU HAD THE SCREEN. I TOOK IT.",
                    status = MessageStatus.APPROVED,
                    createdAtMillis = now,
                )
                val rivalPaid = 0
                val rivalReign = Reign(
                    id = UUID.randomUUID().toString(),
                    owner = nova,
                    message = rivalMessage,
                    startedAtMillis = now,
                    protectedUntilMillis = now + 7_000L,
                    paidCredits = rivalPaid,
                    openingPrice = 0,
                    floorPrice = 0,
                    palette = PaletteKey.ORANGE,
                )
                _world.value = current.copy(
                    reign = rivalReign,
                    currentPrice = rivalReign.openingPrice,
                    appViews = 0,
                    webViews = 0,
                    userDailyReignSeconds = current.userDailyReignSeconds + completed.durationSeconds,
                    history = listOf(completed) + current.history.drop(1),
                    takeoversToday = current.takeoversToday + 1,
                )
                _events.tryEmit(OneEvent.RivalTakeover(completed))
            }
        }
    }

    override suspend fun submitMessage(text: String) {
        val error = AuctionRules.validateMessage(text)
        if (error != null) {
            _events.emit(OneEvent.MessageRejected(error))
            return
        }
        val pending = OneMessage(
            id = UUID.randomUUID().toString(),
            text = text.trim().uppercase(),
            status = MessageStatus.REVIEWING,
            createdAtMillis = System.currentTimeMillis(),
        )
        _world.update { it.copy(messages = listOf(pending) + it.messages) }
        delay(1_450)
        val approved = pending.copy(status = MessageStatus.APPROVED)
        _world.update { world ->
            world.copy(messages = world.messages.map { if (it.id == pending.id) approved else it })
        }
        _events.emit(OneEvent.MessageApproved(approved))
    }

    override suspend fun grantAdReward() {
        delay(2_400)
        _world.update { it.copy(credits = it.credits + 1) }
        _events.emit(OneEvent.CreditsGranted(1, "VERIFIED AD REWARD"))
    }

    override fun grantPurchasedCredits(amount: Int) {
        _world.update { it.copy(credits = it.credits + amount) }
        _events.tryEmit(OneEvent.CreditsGranted(amount, "REVENUECAT PURCHASE"))
    }

    override fun reportCurrentMessage(reason: String) {
        _events.tryEmit(OneEvent.Error("Report received: $reason."))
    }

    override suspend fun blockCurrentOwner() {
        _world.update { current ->
            current.copy(
                reign = current.reign.copy(
                    owner = current.reign.owner.copy(handle = "@BLOCKED", city = "HIDDEN", countryCode = "XX", initials = "--"),
                    message = current.reign.message.copy(text = "CONTENT BLOCKED."),
                ),
                currentContentBlocked = true,
                blockedCount = current.blockedCount + 1,
            )
        }
        _events.emit(OneEvent.Error("Blocked. Their content is now hidden from you."))
    }

    override suspend fun unblockAll() {
        _world.update { it.copy(currentContentBlocked = false, blockedCount = 0) }
        _events.emit(OneEvent.Error("Blocked accounts restored."))
    }

    override suspend fun deleteAccount() {
        _world.update { current ->
            current.copy(
                currentUserId = "user_fresh",
                currentUser = OneOwner("user_fresh", "@PLAYER_FRESH", "EARTH", "XX", false, "PL"),
                credits = 3,
                messages = emptyList(),
                blockedCount = 0,
                currentContentBlocked = false,
            )
        }
        _events.emit(OneEvent.AccountDeleted)
    }

    override suspend fun react(reaction: String) {
        _world.update { current ->
            current.copy(
                reactions = listOf(
                    com.oneglobal.billboard.model.CrowdReaction(reaction, user.handle, System.currentTimeMillis()),
                ) + current.reactions.take(7),
            )
        }
    }

    override suspend fun echoCurrentMessage() {
        val current = _world.value
        val echoed = current.reign.message.copy(
            id = "echo_${UUID.randomUUID()}",
            createdAtMillis = System.currentTimeMillis(),
            timesDeployed = 0,
        )
        _world.update { it.copy(messages = listOf(echoed) + it.messages) }
        _events.emit(OneEvent.MessageApproved(echoed))
    }

    override suspend fun updateHandle(handle: String) {
        val error = AuctionRules.validateHandle(handle)
        if (error != null) {
            _events.emit(OneEvent.HandleRejected(error))
            return
        }
        val normalized = AuctionRules.normalizeHandle(handle)
        val updated = user.copy(handle = "@$normalized", initials = normalized.take(2))
        user = updated
        _world.update { current ->
            current.copy(
                currentUser = updated,
                reign = if (current.reign.owner.id == updated.id) current.reign.copy(owner = updated) else current.reign,
                hall = current.hall.map { entry ->
                    if (entry.owner.id == updated.id) entry.copy(owner = updated) else entry
                },
                history = current.history.map { receipt ->
                    if (receipt.owner.id == updated.id) receipt.copy(owner = updated) else receipt
                },
            )
        }
        _events.emit(OneEvent.HandleUpdated(updated.handle))
    }

    override suspend fun createRecoveryCode() {
        _events.emit(OneEvent.RecoveryCodeCreated("ONE-DEMO-${user.id.takeLast(6).uppercase()}"))
    }

    override suspend fun recoverIdentity(handle: String, code: String) {
        _events.emit(OneEvent.IdentityRejected("Identity recovery requires the live ONE backend."))
    }

    override suspend fun reclaimUnclaimedHandle(handle: String) {
        _events.emit(OneEvent.IdentityRejected("Handle reclaiming requires the live ONE backend."))
    }

    override suspend fun submitFeedback(category: String, text: String) {
        _events.emit(OneEvent.Error("Feedback requires the live ONE backend."))
    }

    fun close() {
        scope.cancel()
    }

    companion object {
        private fun seededHall(): List<HallEntry> {
            val entries = listOf(
                Triple("@MIRA", "I CALLED MY DAD. YOU SHOULD TOO.", 1_142),
                Triple("@KAI", "BE KIND. EVERYONE IS CARRYING SOMETHING.", 934),
                Triple("@AURA", "ATTENTION IS THE LAST SCARCE THING.", 811),
                Triple("@NOVA", "LAGOS TO THE WORLD.", 694),
            )
            return entries.mapIndexed { index, (handle, message, seconds) ->
                HallEntry(
                    rank = index + 1,
                    owner = OneOwner(
                        id = "hall_$index",
                        handle = handle,
                        city = listOf("SEOUL", "BERLIN", "TOKYO", "LAGOS")[index],
                        countryCode = listOf("KR", "DE", "JP", "NG")[index],
                        verified = index < 2,
                        initials = handle.removePrefix("@").take(2),
                    ),
                    message = message,
                    reignSeconds = seconds,
                    verifiedViews = listOf(284_921, 231_407, 198_113, 174_009)[index],
                )
            }
        }
    }
}
