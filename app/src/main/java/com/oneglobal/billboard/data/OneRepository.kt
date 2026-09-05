package com.oneglobal.billboard.data

import com.oneglobal.billboard.model.ChallengePhase
import com.oneglobal.billboard.model.ChallengeResult
import com.oneglobal.billboard.model.WorldState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import com.oneglobal.billboard.model.OneEvent

interface OneRepository {
    val world: StateFlow<WorldState>
    val events: SharedFlow<OneEvent>

    suspend fun challenge(messageId: String, onPhase: (ChallengePhase, String) -> Unit): ChallengeResult
    suspend fun submitMessage(text: String)
    suspend fun grantAdReward()
    fun grantPurchasedCredits(amount: Int)
    fun reportCurrentMessage(reason: String)
    suspend fun blockCurrentOwner()
    suspend fun unblockAll()
    suspend fun deleteAccount()
    suspend fun react(reaction: String)
    suspend fun echoCurrentMessage()
    suspend fun updateHandle(handle: String)
    suspend fun createRecoveryCode()
    suspend fun recoverIdentity(handle: String, code: String)
    suspend fun reclaimUnclaimedHandle(handle: String)
    suspend fun submitFeedback(category: String, text: String)
    suspend fun updateProfile(city: String, countryCode: String)
    suspend fun updatePhoto(jpeg: ByteArray?)
    fun start()
    fun stop()
}
