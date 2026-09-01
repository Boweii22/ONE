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
    suspend fun react(reaction: String)
    suspend fun echoCurrentMessage()
    suspend fun updateHandle(handle: String)
    fun start()
    fun stop()
}
