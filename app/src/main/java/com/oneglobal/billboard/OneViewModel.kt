package com.oneglobal.billboard

import android.app.Application
import android.content.Context
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
    private val profilePreferences = application.getSharedPreferences("one_profile_ui", Context.MODE_PRIVATE)
    private val howToPlayOnLaunch = profilePreferences.getBoolean("how_to_play_on_launch", false)

    private val _ui = MutableStateFlow(
        OneUiState(
            onboardingComplete = !howToPlayOnLaunch && profilePreferences.getBoolean("onboarding_complete", false),
            howToPlayOnLaunch = howToPlayOnLaunch,
        ),
    )
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
                    is OneEvent.HandleUpdated -> {
                        markHandlePromptSeen()
                        _ui.update { current ->
                            val initials = event.handle.removePrefix("@").take(2)
                            val updatedReceipt = current.receipt?.let { receipt ->
                                receipt.copy(owner = receipt.owner.copy(handle = event.handle, initials = initials))
                            }
                            current.copy(
                                overlay = if (current.handleAfterFirstWin && updatedReceipt != null) Overlay.RECEIPT else Overlay.NONE,
                                receipt = updatedReceipt,
                                handleText = "",
                                handleError = null,
                                handleSaving = false,
                                handleAfterFirstWin = false,
                                toast = "${event.handle} IS NOW LIVE",
                            )
                        }
                    }
                    is OneEvent.HandleRejected -> _ui.update {
                        it.copy(handleSaving = false, handleError = event.reason)
                    }
                    is OneEvent.RecoveryCodeCreated -> {
                        val userId = world.value.currentUserId
                        if (userId.isNotBlank()) {
                            profilePreferences.edit().putString("recovery_code_$userId", event.code).commit()
                        }
                        _ui.update {
                            it.copy(recoveryCode = event.code, identityBusy = false, identityError = null)
                        }
                    }
                    is OneEvent.IdentityRecovered -> _ui.update {
                        it.copy(
                            overlay = Overlay.NONE,
                            identityBusy = false,
                            identityError = null,
                            recoveryHandle = "",
                            recoveryCodeInput = "",
                            toast = "${event.handle} IS YOURS AGAIN",
                        )
                    }
                    is OneEvent.IdentityRejected -> _ui.update {
                        it.copy(identityBusy = false, identityError = event.reason)
                    }
                    is OneEvent.CreditsGranted -> _ui.update {
                        it.copy(toast = "+${event.amount} CREDITS // ${event.source}")
                    }
                    is OneEvent.AdSkipGranted -> _ui.update {
                        it.copy(toast = "COOLDOWN SKIP READY // ${event.remainingToday} AD SKIPS LEFT TODAY")
                    }
                    OneEvent.FeedbackSubmitted -> _ui.update {
                        it.copy(
                            overlay = Overlay.NONE,
                            feedbackText = "",
                            feedbackSending = false,
                            toast = "FEEDBACK RECEIVED // THANK YOU",
                        )
                    }
                    OneEvent.AccountDeleted -> {
                        profilePreferences.edit().clear().apply()
                        _ui.value = OneUiState(
                            onboardingComplete = true,
                            tab = MainTab.YOU,
                            toast = "ACCOUNT DELETED // FRESH ANONYMOUS IDENTITY CREATED",
                        )
                    }
                    is OneEvent.Error -> _ui.update {
                        // A failed feedback request used to leave its button permanently
                        // stuck on SENDING. Keep the draft open and make retry immediate.
                        it.copy(
                            toast = event.message,
                            feedbackSending = if (it.overlay == Overlay.FEEDBACK) false else it.feedbackSending,
                        )
                    }
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _ui.update { it.copy(tab = tab, overlay = Overlay.NONE) }
    }

    fun openProfile() { _ui.update { it.copy(overlay = Overlay.PROFILE) } }

    fun saveProfile(city: String, countryCode: String) {
        if (_ui.value.profileBusy) return
        _ui.update { it.copy(profileBusy = true) }
        viewModelScope.launch {
            try {
                repository.updateProfile(city.trim(), countryCode.trim().uppercase())
                _ui.update { it.copy(overlay = Overlay.NONE, toast = "Profile updated") }
            } catch (e: Exception) { showToast("Profile could not be saved. Check your connection and try again.") }
            finally { _ui.update { it.copy(profileBusy = false) } }
        }
    }

    fun updateProfilePhoto(uri: android.net.Uri?) {
        if (_ui.value.profileBusy) return
        _ui.update { it.copy(profileBusy = true) }
        viewModelScope.launch {
            try {
                val jpeg = uri?.let { com.oneglobal.billboard.ui.prepareProfilePhoto(getApplication(), it) }
                repository.updatePhoto(jpeg)
                showToast(if (jpeg == null) "Photo removed" else "Profile photo updated")
            } catch (e: Exception) { showToast("Photo could not be saved. Please try again.") }
            finally { _ui.update { it.copy(profileBusy = false) } }
        }
    }

    fun completeOnboarding() {
        profilePreferences.edit().putBoolean("onboarding_complete", true).apply()
        _ui.update { it.copy(onboardingComplete = true) }
    }

    fun setHowToPlayOnLaunch(enabled: Boolean) {
        profilePreferences.edit().putBoolean("how_to_play_on_launch", enabled).apply()
        _ui.update { it.copy(howToPlayOnLaunch = enabled) }
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
                    if (shouldPromptForHandle()) {
                        _ui.update {
                            it.copy(
                                overlay = Overlay.HANDLE,
                                handleText = "",
                                handleError = null,
                                handleSaving = false,
                                handleAfterFirstWin = true,
                            )
                        }
                    } else {
                        _ui.update { it.copy(overlay = Overlay.RECEIPT) }
                    }
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
        if (_ui.value.overlay == Overlay.RECEIPT &&
            !profilePreferences.getBoolean("google_prompt_seen", false)) {
            profilePreferences.edit().putBoolean("google_prompt_seen", true).commit()
            openIdentityBackup()
            return
        }
        if (_ui.value.overlay == Overlay.HANDLE) {
            dismissHandleEditor()
            return
        }
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
        if (reaction in world.value.myReactions) { showToast("You've already sent this reaction for this reign."); return }
        viewModelScope.launch { repository.react(reaction) }
    }

    fun echoCurrentMessage() {
        viewModelScope.launch { repository.echoCurrentMessage() }
    }

    fun updateHandle(handle: String) {
        viewModelScope.launch { repository.updateHandle(handle) }
    }

    fun openHandleEditor() {
        val existing = world.value.currentUser?.handle.orEmpty()
        _ui.update {
            it.copy(
                overlay = Overlay.HANDLE,
                handleText = existing.takeUnless(::isFallbackHandle).orEmpty().removePrefix("@"),
                handleError = null,
                handleSaving = false,
                handleAfterFirstWin = false,
            )
        }
    }

    fun updateHandleText(raw: String) {
        if (raw.length > AuctionRules.HANDLE_MAX + 1) return
        val candidate = raw.removePrefix("@").uppercase()
        _ui.update {
            it.copy(
                handleText = candidate,
                handleError = AuctionRules.validateHandle(candidate).takeIf { candidate.isNotBlank() },
            )
        }
    }

    fun submitHandle(country: String) {
        if (_ui.value.handleSaving) return
        val candidate = AuctionRules.normalizeHandle(_ui.value.handleText)
        val error = AuctionRules.validateHandle(candidate)
        if (error != null) {
            _ui.update { it.copy(handleError = error) }
            return
        }
        _ui.update { it.copy(handleText = candidate, handleError = null, handleSaving = true) }
        viewModelScope.launch {
            try {
                repository.updateProfile("", country)
                repository.updateHandle(candidate)
            } catch (e: Exception) {
                _ui.update { it.copy(handleSaving = false, handleError = "Could not save country. Please retry.") }
            }
        }
    }

    fun dismissHandleEditor() {
        val afterFirstWin = _ui.value.handleAfterFirstWin
        if (afterFirstWin) markHandlePromptSeen()
        _ui.update {
            it.copy(
                overlay = if (afterFirstWin && it.receipt != null) Overlay.RECEIPT else Overlay.NONE,
                handleText = "",
                handleError = null,
                handleSaving = false,
                handleAfterFirstWin = false,
            )
        }
    }

    fun openVault() {
        _ui.update { it.copy(overlay = Overlay.VAULT) }
    }

    fun redeemAdReward() {
        viewModelScope.launch { repository.grantAdReward() }
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

    fun identityStatus(message: String) {
        _ui.update { it.copy(identityError = message, identityBusy = false) }
    }

    fun beginGoogleIdentity() {
        _ui.update { it.copy(identityError = "Choose a Google account to protect your handle.", identityBusy = true) }
    }

    fun googleIdentity(token: String, nonce: String, restore: Boolean) {
        _ui.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withTimeout(30_000) { repository.googleIdentity(token, nonce, restore) }
                profilePreferences.edit().putBoolean("google_linked_${world.value.currentUserId}", true).apply()
                _ui.update { it.copy(overlay = Overlay.IDENTITY, identityLinked = true, identityError = null, toast = "Google connected. Your identity is saved.") }
            } catch (e: Exception) {
                _ui.update { it.copy(identityError = e.message ?: "Could not connect Google.") }
            } finally { _ui.update { it.copy(identityBusy = false) } }
        }
    }

    fun openIdentityBackup() {
        val userId = world.value.currentUserId
        _ui.update {
            it.copy(
                overlay = Overlay.IDENTITY,
                // Local preference is only an optimistic cache. The auth profile
                // is the authority after a reinstall or a different device.
                identityLinked = profilePreferences.getBoolean("google_linked_$userId", false),
                recoveryCode = userId.takeIf(String::isNotBlank)
                    ?.let { id -> profilePreferences.getString("recovery_code_$id", null) },
                recoveryHandle = "",
                recoveryCodeInput = "",
                identityBusy = false,
                identityError = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.isGoogleIdentityLinked() }
                .onSuccess { linked ->
                    if (linked) profilePreferences.edit().putBoolean("google_linked_$userId", true).apply()
                    _ui.update { current ->
                        if (current.overlay == Overlay.IDENTITY) current.copy(identityLinked = linked) else current
                    }
                }
        }
    }

    fun createRecoveryCode() {
        if (_ui.value.identityBusy || _ui.value.recoveryCode != null) return
        _ui.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch { repository.createRecoveryCode() }
    }

    fun updateRecoveryHandle(value: String) {
        _ui.update { it.copy(recoveryHandle = value.removePrefix("@").uppercase(), identityError = null) }
    }

    fun updateRecoveryCode(value: String) {
        _ui.update { it.copy(recoveryCodeInput = value.uppercase(), identityError = null) }
    }

    fun recoverIdentity() {
        if (_ui.value.identityBusy) return
        val handle = AuctionRules.normalizeHandle(_ui.value.recoveryHandle)
        val code = _ui.value.recoveryCodeInput.trim()
        if (AuctionRules.validateHandle(handle) != null || code.length < 10) {
            _ui.update { it.copy(identityError = "Enter the handle and the recovery code exactly as saved.") }
            return
        }
        _ui.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch { repository.recoverIdentity(handle, code) }
    }

    fun reclaimUnclaimedHandle() {
        if (_ui.value.identityBusy) return
        val handle = AuctionRules.normalizeHandle(_ui.value.recoveryHandle)
        if (AuctionRules.validateHandle(handle) != null) {
            _ui.update { it.copy(identityError = "Enter the old handle you want to reclaim.") }
            return
        }
        _ui.update { it.copy(identityBusy = true, identityError = null) }
        viewModelScope.launch { repository.reclaimUnclaimedHandle(handle) }
    }

    fun openHowItWorks() {
        _ui.update { it.copy(overlay = Overlay.HOW_IT_WORKS) }
    }

    fun openFeedback() {
        _ui.update { it.copy(overlay = Overlay.FEEDBACK, feedbackText = "", feedbackCategory = "OTHER", feedbackSending = false) }
    }

    fun updateFeedbackText(text: String) {
        if (text.length <= 1_000) _ui.update { it.copy(feedbackText = text) }
    }

    fun selectFeedbackCategory(category: String) {
        _ui.update { it.copy(feedbackCategory = category) }
    }

    fun submitFeedback() {
        val text = _ui.value.feedbackText.trim()
        if (text.length < 3) {
            _ui.update { it.copy(toast = "WRITE A LITTLE MORE BEFORE SENDING.") }
            return
        }
        if (_ui.value.feedbackSending) return
        _ui.update { it.copy(feedbackSending = true) }
        viewModelScope.launch { repository.submitFeedback(_ui.value.feedbackCategory, text) }
    }

    fun blockCurrentOwner() {
        _ui.update { it.copy(overlay = Overlay.NONE) }
        viewModelScope.launch { repository.blockCurrentOwner() }
    }

    fun unblockAll() {
        viewModelScope.launch { repository.unblockAll() }
    }

    fun openDeleteAccount() {
        _ui.update { it.copy(overlay = Overlay.DELETE_ACCOUNT) }
    }

    fun deleteAccount() {
        if (_ui.value.accountDeleting) return
        _ui.update { it.copy(accountDeleting = true) }
        viewModelScope.launch {
            repository.deleteAccount()
            _ui.update { it.copy(accountDeleting = false) }
        }
    }

    fun enablePush(onRequestPermission: ((Boolean) -> Unit) -> Unit) {
        onRequestPermission { granted ->
            _ui.update {
                it.copy(
                    pushEnabled = granted,
                    toast = if (granted) {
                        "REVENGE ALERTS ARMED"
                    } else {
                        "NOTIFICATIONS ARE OFF — ENABLE THEM IN SETTINGS TO ARM ALERTS"
                    },
                )
            }
        }
    }

    fun syncPushPermission(enabled: Boolean) {
        _ui.update { it.copy(pushEnabled = enabled) }
    }

    fun syncPushRegistration(registered: Boolean) {
        _ui.update { it.copy(pushRegistered = registered) }
    }

    fun dismissToast() {
        _ui.update { it.copy(toast = null) }
    }

    fun showToast(message: String) {
        _ui.update { it.copy(toast = message) }
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

    private fun shouldPromptForHandle(): Boolean {
        val user = world.value.currentUser ?: return false
        if (!isFallbackHandle(user.handle)) return false
        return !profilePreferences.getBoolean("handle_prompted_${user.id}", false)
    }

    private fun markHandlePromptSeen() {
        val userId = world.value.currentUserId.takeIf { it.isNotBlank() } ?: return
        profilePreferences.edit().putBoolean("handle_prompted_$userId", true).apply()
    }

    private fun isFallbackHandle(handle: String): Boolean = handle.startsWith("@PLAYER_")
}
