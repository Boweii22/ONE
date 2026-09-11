package com.oneglobal.billboard.model

enum class MainTab {
    LIVE,
    LIBRARY,
    HALL,
    YOU,
}

enum class Overlay {
    NONE,
    CHALLENGE,
    RECEIPT,
    HANDLE,
    COMPOSE,
    VAULT,
    REPORT,
    FEEDBACK,
    IDENTITY,
    DELETE_ACCOUNT,
    HOW_IT_WORKS,
    PROFILE,
    RESTRICTED,
}

enum class MessageStatus {
    APPROVED,
    REVIEWING,
    REJECTED,
    REVOKED,
}

enum class ChallengePhase {
    IDLE,
    RESERVING,
    VERIFYING,
    SPENDING,
    COMMITTING,
    WON,
    FAILED,
}

enum class PaletteKey {
    ACID,
    COBALT,
    ORANGE,
    MAGENTA,
    ICE,
}

data class OneOwner(
    val id: String,
    val handle: String,
    val city: String,
    val countryCode: String,
    val verified: Boolean,
    val initials: String,
    val photoVersion: String? = null,
)

data class OneMessage(
    val id: String,
    val text: String,
    val status: MessageStatus,
    val createdAtMillis: Long,
    val timesDeployed: Int = 0,
    val rejectionReason: String? = null,
)

data class Reign(
    val id: String,
    val sequence: Long = 0,
    val owner: OneOwner,
    val message: OneMessage,
    val startedAtMillis: Long,
    val protectedUntilMillis: Long,
    val paidCredits: Int,
    val openingPrice: Int,
    val floorPrice: Int,
    val palette: PaletteKey,
    val usedRevengeTicket: Boolean = false,
)

data class TakeoverActivity(
    val sequence: Long,
    val owner: String,
    val message: String,
    val startedAtMillis: Long,
    val usedRevengeTicket: Boolean,
)

data class CrowdReaction(
    val reaction: String,
    val handle: String,
    val createdAtMillis: Long,
)

data class HallEntry(
    val rank: Int,
    val owner: OneOwner,
    val message: String,
    val reignSeconds: Int,
    val verifiedViews: Int,
    val takeovers: Int? = null,
)

data class ReignReceipt(
    val owner: OneOwner,
    val message: String,
    val paidCredits: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val appViews: Int,
    val webViews: Int,
    val previousOwner: String,
    val dethronedBy: String? = null,
) {
    val totalViews: Int get() = appViews + webViews
    val durationSeconds: Int
        get() = (((endedAtMillis ?: System.currentTimeMillis()) - startedAtMillis) / 1_000L)
            .toInt()
            .coerceAtLeast(0)
}

data class WorldState(
    val reign: Reign,
    val currentPrice: Int,
    val appViews: Int,
    val webViews: Int,
    val liveWatchers: Int,
    val credits: Int,
    val userDailyReignSeconds: Int,
    val userRetakesToday: Int,
    val dailyCapSeconds: Int,
    val messages: List<OneMessage>,
    val hall: List<HallEntry>,
    val history: List<ReignReceipt>,
    val connected: Boolean = true,
    val demoMode: Boolean = true,
    val currentUserId: String = "user_bowei",
    val currentUser: OneOwner? = null,
    val takeBalance: Int = 2,
    val takeBalanceCap: Int = 2,
    val takeRefillSeconds: Int = 0,
    val takeAdBypassesRemainingToday: Int = 2,
    val takeBypassCreditCost: Int = 1,
    val takeoversToday: Int = 0,
    val activity: List<TakeoverActivity> = emptyList(),
    val reactions: List<CrowdReaction> = emptyList(),
    val currentContentBlocked: Boolean = false,
    val blockedCount: Int = 0,
    val hallToday: List<HallEntry> = emptyList(),
    val hallAllTime: List<HallEntry> = emptyList(),
    val hallAllTimeLive: Boolean = false,
    val reactionCounts: Map<String, Int>? = null,
    val myReactions: Set<String> = emptySet(),
    val userTakeovers: Int? = null,
    val userLongestReign: Int? = null,
    val userVerifiedViews: Int? = null,
    val blockedAccounts: List<BlockedAccount> = emptyList(),
    val accountRestriction: String? = null,
    val accountRestrictionReason: String? = null,
    val accountSuspendedUntilMillis: Long? = null,
    val accountOffenseCount: Int = 0,
    val lastConnectedAtMillis: Long = System.currentTimeMillis(),
)

data class BlockedAccount(val id: String, val handle: String)

data class OneUiState(
    val onboardingComplete: Boolean = false,
    val tab: MainTab = MainTab.LIVE,
    val overlay: Overlay = Overlay.NONE,
    val selectedMessageId: String? = null,
    val challengePhase: ChallengePhase = ChallengePhase.IDLE,
    val challengeStatus: String = "",
    val receipt: ReignReceipt? = null,
    val composeText: String = "",
    val composeError: String? = null,
    val handleText: String = "",
    val handleError: String? = null,
    val handleSaving: Boolean = false,
    val handleAfterFirstWin: Boolean = false,
    val pendingPushPrompt: Boolean = false,
    val pushPromptVisible: Boolean = false,
    val toast: String? = null,
    val revengeBanner: String? = null,
    val adProgress: Float = 0f,
    val adPlaying: Boolean = false,
    val pushEnabled: Boolean = false,
    val pushBlocked: Boolean = false,
    val pushRegistered: Boolean = false,
    val takeoverPulse: Int = 0,
    val accountDeleting: Boolean = false,
    val feedbackCategory: String = "OTHER",
    val feedbackText: String = "",
    val feedbackSending: Boolean = false,
    val recoveryCode: String? = null,
    val recoveryHandle: String = "",
    val recoveryCodeInput: String = "",
    val identityBusy: Boolean = false,
    val identityLinked: Boolean = false,
    val identityError: String? = null,
    val profileBusy: Boolean = false,
    val howToPlayOnLaunch: Boolean = false,
    val offlineVisible: Boolean = false,
    val reconnecting: Boolean = false,
)

sealed interface OneEvent {
    data class TakeoverWon(val receipt: ReignReceipt) : OneEvent
    data class RivalTakeover(val receipt: ReignReceipt) : OneEvent
    data class MessageApproved(val message: OneMessage) : OneEvent
    data class MessageRejected(val reason: String) : OneEvent
    data class HandleUpdated(val handle: String) : OneEvent
    data class HandleRejected(val reason: String) : OneEvent
    data class RecoveryCodeCreated(val code: String) : OneEvent
    data class IdentityRecovered(val handle: String) : OneEvent
    data class IdentityRejected(val reason: String) : OneEvent
    data class CreditsGranted(val amount: Int, val source: String) : OneEvent
    data class TakeRefillBypassed(val method: String) : OneEvent
    data object FeedbackSubmitted : OneEvent
    data object AccountDeleted : OneEvent
    data class Error(val message: String) : OneEvent
}

sealed interface ChallengeResult {
    data class Success(val receipt: ReignReceipt) : ChallengeResult
    // retryable marks a failure that looks like a connectivity/timeout problem
    // rather than a definitive server answer - the caller should reuse the same
    // idempotency key on retry instead of minting a new one, so a request that
    // actually landed server-side can't be double-applied by a retry.
    data class Failure(val reason: String, val retryable: Boolean = false) : ChallengeResult
}
