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
    COMPOSE,
    VAULT,
    REPORT,
}

enum class MessageStatus {
    APPROVED,
    REVIEWING,
    REJECTED,
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
)

data class OneMessage(
    val id: String,
    val text: String,
    val status: MessageStatus,
    val createdAtMillis: Long,
    val timesDeployed: Int = 0,
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
    val cooldownRemainingSeconds: Int = 0,
    val takeoversToday: Int = 0,
    val activity: List<TakeoverActivity> = emptyList(),
    val reactions: List<CrowdReaction> = emptyList(),
)

data class OneUiState(
    val tab: MainTab = MainTab.LIVE,
    val overlay: Overlay = Overlay.NONE,
    val selectedMessageId: String? = null,
    val challengePhase: ChallengePhase = ChallengePhase.IDLE,
    val challengeStatus: String = "",
    val receipt: ReignReceipt? = null,
    val composeText: String = "",
    val composeError: String? = null,
    val toast: String? = null,
    val revengeBanner: String? = null,
    val adProgress: Float = 0f,
    val adPlaying: Boolean = false,
    val pushEnabled: Boolean = false,
    val takeoverPulse: Int = 0,
)

sealed interface OneEvent {
    data class TakeoverWon(val receipt: ReignReceipt) : OneEvent
    data class RivalTakeover(val receipt: ReignReceipt) : OneEvent
    data class MessageApproved(val message: OneMessage) : OneEvent
    data class MessageRejected(val reason: String) : OneEvent
    data class CreditsGranted(val amount: Int, val source: String) : OneEvent
    data class Error(val message: String) : OneEvent
}

sealed interface ChallengeResult {
    data class Success(val receipt: ReignReceipt) : ChallengeResult
    data class Failure(val reason: String) : ChallengeResult
}
