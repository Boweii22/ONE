package com.oneglobal.billboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oneglobal.billboard.data.AuctionRules
import com.oneglobal.billboard.data.CloudOneRepository
import com.oneglobal.billboard.data.DemoOneRepository
import com.oneglobal.billboard.data.OneRepository
import com.oneglobal.billboard.model.ChallengePhase
import com.oneglobal.billboard.model.ChallengeResult
import com.oneglobal.billboard.model.MainTab
import com.oneglobal.billboard.model.MessageStatus
import com.oneglobal.billboard.model.OneEvent
import com.oneglobal.billboard.model.OneUiState
import com.oneglobal.billboard.model.Overlay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OneViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OneRepository = if (CloudOneRepository.isConfigured()) {
        CloudOneRepository(application)
    } else {
        DemoOneRepository()
    }
    val world = repository.world

    private val _ui = MutableStateFlow(OneUiState())
    val ui: StateFlow<OneUiState> = _ui.asStateFlow()

    init {
        repository.start()
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is OneEvent.TakeoverWon -> Unit
                    is OneEvent.RivalTakeover -> {
                        _ui.update {
                            it.copy(
                                receipt = event.receipt,
                                revengeBanner = "${event.receipt.dethronedBy} TOOK ONE FROM YOU",
                                toast = "Your reign reached ${event.receipt.totalViews} verified views.",
                            )
                        }
                    }
                    is OneEvent.MessageApproved -> {
                        _ui.update {
                            it.copy(
                                overlay = Overlay.NONE,
                                composeText = "",
                                composeError = null,
                                selectedMessageId = event.message.id,
                                toast = "MESSAGE APPROVED // READY TO DEPLOY",
                            )
                        }
                    }
                    is OneEvent.MessageRejected -> _ui.update {
                        it.copy(composeError = event.reason, toast = event.reason)
                    }
                    is OneEvent.CreditsGranted -> _ui.update {
                        it.copy(toast = "+${event.amount} CREDITS // ${event.source}")
                    }
                    is OneEvent.Error -> _ui.update { it.copy(toast = event.message) }
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _ui.update { it.copy(tab = tab, overlay = Overlay.NONE) }
    }

    fun openChallenge() {
        val selected = _ui.value.selectedMessageId
            ?: world.value.messages.firstOrNull { it.status == MessageStatus.APPROVED }?.id
        _ui.update {
            it.copy(
                overlay = Overlay.CHALLENGE,
                selectedMessageId = selected,
                challengePhase = ChallengePhase.IDLE,
                challengeStatus = "",
            )
        }
    }

    fun selectMessage(messageId: String) {
        _ui.update { it.copy(selectedMessageId = messageId) }
    }

    fun beginChallenge() {
        if (_ui.value.challengePhase !in listOf(ChallengePhase.IDLE, ChallengePhase.FAILED)) return
        val messageId = _ui.value.selectedMessageId ?: return
        viewModelScope.launch {
            val result = repository.challenge(messageId) { phase, status ->
                _ui.update { it.copy(challengePhase = phase, challengeStatus = status) }
            }
            when (result) {
                is ChallengeResult.Success -> {
                    _ui.update {
                        it.copy(
                            challengePhase = ChallengePhase.WON,
                            challengeStatus = "YOU OWN THE ONLY SCREEN",
                            receipt = result.receipt,
                            takeoverPulse = it.takeoverPulse + 1,
                        )
                    }
                    delay(2_100)
                    _ui.update { it.copy(overlay = Overlay.RECEIPT) }
                }
                is ChallengeResult.Failure -> _ui.update {
                    it.copy(
                        challengePhase = ChallengePhase.FAILED,
                        challengeStatus = result.reason,
                        toast = result.reason,
                    )
                }
            }
        }
    }

    fun closeOverlay() {
        _ui.update {
            it.copy(
                overlay = Overlay.NONE,
                challengePhase = ChallengePhase.IDLE,
                challengeStatus = "",
                composeError = null,
            )
        }
    }

    fun openCompose() {
        _ui.update { it.copy(overlay = Overlay.COMPOSE, composeText = "", composeError = null) }
    }

    fun updateComposeText(text: String) {
        if (text.length <= AuctionRules.MESSAGE_LIMIT + 12) {
            _ui.update {
                it.copy(
                    composeText = text,
                    composeError = AuctionRules.validateMessage(text)?.takeIf { error -> text.isNotBlank() && text.length >= 8 },
                )
            }
        }
    }

    fun submitMessage() {
        val text = _ui.value.composeText
        val error = AuctionRules.validateMessage(text)
        if (error != null) {
            _ui.update { it.copy(composeError = error) }
            return
        }
        _ui.update { it.copy(composeError = null, toast = "SCREENING MESSAGE ON-DEVICE…") }
        viewModelScope.launch { repository.submitMessage(text) }
    }

    fun react(reaction: String) {
        viewModelScope.launch { repository.react(reaction) }
    }

    fun echoCurrentMessage() {
        viewModelScope.launch { repository.echoCurrentMessage() }
    }

    fun updateHandle(handle: String) {
        viewModelScope.launch { repository.updateHandle(handle) }
    }

    fun openVault() {
        _ui.update { it.copy(overlay = Overlay.VAULT) }
    }

    fun watchRewardedAd() {
        if (_ui.value.adPlaying) return
        viewModelScope.launch {
            _ui.update { it.copy(adPlaying = true, adProgress = 0f) }
            coroutineScope {
                val reward = async { repository.grantAdReward() }
                repeat(12) { step ->
                    delay(200)
                    _ui.update { it.copy(adProgress = (step + 1) / 12f) }
                }
                reward.await()
            }
            _ui.update { it.copy(adPlaying = false, adProgress = 1f) }
        }
    }

    fun grantPurchasedCredits(amount: Int) {
        repository.grantPurchasedCredits(amount)
    }

    fun openReport() {
        _ui.update { it.copy(overlay = Overlay.REPORT) }
    }

    fun report(reason: String) {
        repository.reportCurrentMessage(reason)
        _ui.update { it.copy(overlay = Overlay.NONE) }
    }

    fun enablePush(onRequestPermission: () -> Unit) {
        onRequestPermission()
        _ui.update {
            it.copy(
                pushEnabled = true,
                toast = "REVENGE ALERTS ARMED",
            )
        }
    }

    fun dismissToast() {
        _ui.update { it.copy(toast = null) }
    }

    fun dismissRevenge() {
        _ui.update { it.copy(revengeBanner = null) }
    }

    fun openLastReceipt() {
        if (_ui.value.receipt != null) {
            _ui.update { it.copy(overlay = Overlay.RECEIPT, revengeBanner = null) }
        }
    }

    override fun onCleared() {
        repository.stop()
        when (repository) {
            is DemoOneRepository -> repository.close()
            is CloudOneRepository -> repository.close()
        }
        super.onCleared()
    }
}
