package com.oneglobal.billboard.data

import android.content.Context
import com.oneglobal.billboard.BuildConfig
import com.oneglobal.billboard.model.ChallengePhase
import com.oneglobal.billboard.model.ChallengeResult
import com.oneglobal.billboard.model.CrowdReaction
import com.oneglobal.billboard.model.HallEntry
import com.oneglobal.billboard.model.MessageStatus
import com.oneglobal.billboard.model.OneEvent
import com.oneglobal.billboard.model.OneMessage
import com.oneglobal.billboard.model.OneOwner
import com.oneglobal.billboard.model.PaletteKey
import com.oneglobal.billboard.model.Reign
import com.oneglobal.billboard.model.ReignReceipt
import com.oneglobal.billboard.model.TakeoverActivity
import com.oneglobal.billboard.model.WorldState
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production repository. Phones never mutate ownership directly: every action
 * calls a Postgres function that serializes ONE and returns an idempotent result.
 */
class CloudOneRepository(context: Context) : OneRepository {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    private val preferences = appContext.getSharedPreferences("one_cloud_session", Context.MODE_PRIVATE)
    private var pollJob: Job? = null
    private var heartbeatJob: Job? = null

    private val _world = MutableStateFlow(disconnectedWorld())
    override val world: StateFlow<WorldState> = _world.asStateFlow()

    private val _events = MutableSharedFlow<OneEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<OneEvent> = _events.asSharedFlow()

    override fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            runCatching {
                ensureSession()
                rpc("ensure_profile", JSONObject(), authenticated = true)
                refreshWorld()
            }.onFailure { publishConnectionError(it) }

            while (isActive) {
                delay(1_500)
                runCatching { refreshWorld() }.onFailure { markDisconnected() }
            }
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                runCatching { rpc("heartbeat", JSONObject().put("p_source", "app"), authenticated = true) }
                delay(8_000)
            }
        }
    }

    override fun stop() {
        pollJob?.cancel()
        heartbeatJob?.cancel()
        pollJob = null
        heartbeatJob = null
    }

    override suspend fun challenge(
        messageId: String,
        onPhase: (ChallengePhase, String) -> Unit,
    ): ChallengeResult {
        val before = _world.value
        val selected = before.messages.firstOrNull { it.id == messageId && it.status == MessageStatus.APPROVED }
            ?: return ChallengeResult.Failure("Choose an approved message first.")
        return runCatching {
            onPhase(ChallengePhase.RESERVING, "LOCKING THE CURRENT REIGN")
            val requestId = UUID.randomUUID().toString()
            onPhase(ChallengePhase.VERIFYING, "CHECKING MESSAGE + COOLDOWN")
            if (before.cooldownRemainingSeconds > 0) {
                onPhase(ChallengePhase.SPENDING, "VERIFYING ONE REVENGE TICKET")
            } else {
                onPhase(ChallengePhase.SPENDING, "FREE TAKEOVER // NOTHING TO SPEND")
            }
            val result = rpc(
                "take_one",
                JSONObject()
                    .put("p_message_id", messageId)
                    .put("p_expected_sequence", before.reign.sequence)
                    .put("p_request_id", requestId),
                authenticated = true,
            )
            if (!result.optBoolean("ok")) {
                return ChallengeResult.Failure(result.optString("message", "Someone else reached ONE first."))
            }
            onPhase(ChallengePhase.COMMITTING, "COMMITTED BY THE GLOBAL LEDGER")
            refreshWorld()
            val now = result.optLong("started_at_ms", System.currentTimeMillis())
            val receipt = ReignReceipt(
                owner = _world.value.currentUser ?: fallbackUser(before.currentUserId),
                message = selected.text,
                paidCredits = if (result.optBoolean("used_revenge_ticket")) 1 else 0,
                startedAtMillis = now,
                appViews = 0,
                webViews = 0,
                previousOwner = before.reign.owner.handle,
            )
            _events.emit(OneEvent.TakeoverWon(receipt))
            ChallengeResult.Success(receipt)
        }.getOrElse { error ->
            ChallengeResult.Failure(error.userMessage())
        }
    }

    override suspend fun submitMessage(text: String) {
        runCatching {
            val result = rpc("submit_message", JSONObject().put("p_text", text), authenticated = true)
            refreshWorld()
            val message = parseMessage(result)
            if (message.status == MessageStatus.APPROVED) {
                _events.emit(OneEvent.MessageApproved(message))
            } else {
                _events.emit(OneEvent.MessageRejected("Message queued for human safety review."))
            }
        }.onFailure { _events.emit(OneEvent.MessageRejected(it.userMessage())) }
    }

    override suspend fun grantAdReward() {
        _events.emit(OneEvent.Error("Ad rewards are granted only after RevenueCat server verification."))
        refreshWorld()
    }

    override fun grantPurchasedCredits(amount: Int) {
        scope.launch {
            delay(700)
            runCatching { refreshWorld() }
            _events.emit(OneEvent.Error("Purchase received. The server is verifying your Revenge Tickets."))
        }
    }

    override fun reportCurrentMessage(reason: String) {
        scope.launch {
            runCatching { rpc("report_one", JSONObject().put("p_reason", reason), authenticated = true) }
                .onSuccess { _events.emit(OneEvent.Error("Report received. Thank you for protecting ONE.")) }
                .onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
        }
    }

    override suspend fun react(reaction: String) {
        runCatching {
            rpc("react_to_one", JSONObject().put("p_reaction", reaction), authenticated = true)
            refreshWorld()
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override suspend fun echoCurrentMessage() {
        runCatching {
            val result = rpc("echo_current_message", JSONObject(), authenticated = true)
            refreshWorld()
            val echoed = OneMessage(
                id = result.getString("id"),
                text = result.getString("text"),
                status = MessageStatus.APPROVED,
                createdAtMillis = System.currentTimeMillis(),
            )
            _events.emit(OneEvent.MessageApproved(echoed))
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override suspend fun updateHandle(handle: String) {
        runCatching {
            val result = rpc("update_handle", JSONObject().put("p_handle", handle), authenticated = true)
            refreshWorld()
            _events.emit(OneEvent.HandleUpdated(result.optString("handle", "@$handle")))
        }.onFailure { _events.emit(OneEvent.HandleRejected(it.userMessage())) }
    }

    private suspend fun refreshWorld() {
        val before = _world.value
        val next = parseWorld(rpc("get_one_state", JSONObject(), authenticated = true))
        _world.value = next
        if (
            before.connected &&
            before.reign.owner.id == before.currentUserId &&
            next.reign.owner.id != before.currentUserId &&
            before.reign.id != next.reign.id
        ) {
            val endedAt = System.currentTimeMillis()
            _events.emit(
                OneEvent.RivalTakeover(
                    ReignReceipt(
                        owner = before.currentUser ?: fallbackUser(before.currentUserId),
                        message = before.reign.message.text,
                        paidCredits = if (before.reign.usedRevengeTicket) 1 else 0,
                        startedAtMillis = before.reign.startedAtMillis,
                        endedAtMillis = endedAt,
                        appViews = before.appViews,
                        webViews = before.webViews,
                        previousOwner = "@ONE",
                        dethronedBy = next.reign.owner.handle,
                    ),
                ),
            )
        }
    }

    private suspend fun ensureSession(): AuthSession = authMutex.withLock {
        val storedAccess = preferences.getString("access_token", null)
        val storedRefresh = preferences.getString("refresh_token", null)
        if (!storedAccess.isNullOrBlank()) return AuthSession(storedAccess, storedRefresh.orEmpty())

        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/signup", "POST", null)
        connection.outputStream.use { it.write("{}".toByteArray()) }
        val payload = connection.readJson()
        if (connection.responseCode !in 200..299) throw ApiException(connection.responseCode, payload.optString("msg", "Anonymous sign-in failed."))
        return persistSession(payload)
    }

    private suspend fun refreshSession(): AuthSession = authMutex.withLock {
        val refresh = preferences.getString("refresh_token", null)
            ?: throw ApiException(401, "Missing refresh token.")
        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token", "POST", null)
        connection.outputStream.use { stream ->
            stream.write(JSONObject().put("refresh_token", refresh).toString().toByteArray())
        }
        val payload = connection.readJson()
        if (connection.responseCode !in 200..299) {
            preferences.edit().clear().apply()
            throw ApiException(connection.responseCode, "Session refresh failed.")
        }
        persistSession(payload)
    }

    private fun persistSession(payload: JSONObject): AuthSession {
        val session = AuthSession(payload.getString("access_token"), payload.optString("refresh_token"))
        preferences.edit().putString("access_token", session.accessToken).putString("refresh_token", session.refreshToken).apply()
        return session
    }

    private suspend fun rpc(name: String, body: JSONObject, authenticated: Boolean, retry: Boolean = true): JSONObject {
        val token = if (authenticated) ensureSession().accessToken else BuildConfig.SUPABASE_ANON_KEY
        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/$name", "POST", token)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val payload = connection.readJson()
        if (connection.responseCode == 401 && authenticated && retry) {
            refreshSession()
            return rpc(name, body, authenticated, retry = false)
        }
        if (connection.responseCode !in 200..299) {
            throw ApiException(connection.responseCode, payload.optString("message", payload.optString("hint", "ONE request failed.")))
        }
        return payload
    }

    private fun open(url: String, method: String, token: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            doInput = true
            doOutput = method != "GET"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.readJson(): JSONObject {
        val source = if (responseCode in 200..299) inputStream else errorStream
        val text = source?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun parseWorld(json: JSONObject): WorldState {
        val reignJson = json.getJSONObject("reign")
        val owner = parseOwner(reignJson.getJSONObject("owner"))
        val messageJson = reignJson.getJSONObject("message")
        val message = OneMessage(
            id = messageJson.getString("id"),
            text = messageJson.getString("text"),
            status = MessageStatus.APPROVED,
            createdAtMillis = reignJson.getLong("started_at_ms"),
            timesDeployed = messageJson.optInt("times_deployed"),
        )
        val currentUserJson = json.optJSONObject("current_user")
        val hall = json.optJSONArray("hall").objects().mapIndexed { index, item ->
            HallEntry(
                rank = index + 1,
                owner = OneOwner(
                    id = item.optString("owner_id"),
                    handle = item.optString("owner", "@ONE"),
                    city = item.optString("city", "EARTH"),
                    countryCode = item.optString("country_code", "XX"),
                    verified = false,
                    initials = item.optString("initials", "ON"),
                ),
                message = item.optString("message"),
                reignSeconds = item.optInt("reign_seconds"),
                verifiedViews = item.optInt("verified_views"),
            )
        }.ifEmpty { listOf(placeholderHall(owner, message)) }

        return WorldState(
            reign = Reign(
                id = reignJson.getString("id"),
                sequence = reignJson.getLong("sequence"),
                owner = owner,
                message = message,
                startedAtMillis = reignJson.getLong("started_at_ms"),
                protectedUntilMillis = reignJson.getLong("protected_until_ms"),
                paidCredits = if (reignJson.optBoolean("used_revenge_ticket")) 1 else 0,
                openingPrice = 0,
                floorPrice = 0,
                palette = runCatching { PaletteKey.valueOf(reignJson.optString("palette", "ACID")) }.getOrDefault(PaletteKey.ACID),
                usedRevengeTicket = reignJson.optBoolean("used_revenge_ticket"),
            ),
            currentPrice = 0,
            appViews = json.optInt("app_views"),
            webViews = json.optInt("web_views"),
            liveWatchers = json.optInt("live_watchers"),
            credits = json.optInt("revenge_tickets"),
            userDailyReignSeconds = 0,
            userRetakesToday = 0,
            dailyCapSeconds = 1,
            messages = json.optJSONArray("messages").objects().map(::parseMessage),
            hall = hall,
            history = emptyList(),
            connected = true,
            demoMode = false,
            currentUserId = json.optString("current_user_id"),
            currentUser = currentUserJson?.let(::parseOwner),
            cooldownRemainingSeconds = json.optInt("cooldown_remaining_seconds"),
            takeoversToday = json.optInt("takeovers_today"),
            activity = json.optJSONArray("activity").objects().map { item ->
                TakeoverActivity(
                    sequence = item.optLong("sequence"),
                    owner = item.optString("owner"),
                    message = item.optString("message"),
                    startedAtMillis = item.optLong("started_at_ms"),
                    usedRevengeTicket = item.optBoolean("used_ticket"),
                )
            },
            reactions = json.optJSONArray("reactions").objects().map { item ->
                CrowdReaction(item.optString("reaction"), item.optString("handle"), item.optLong("created_at_ms"))
            },
        )
    }

    private fun parseOwner(json: JSONObject) = OneOwner(
        id = json.optString("id"),
        handle = json.optString("handle", "@ONE"),
        city = json.optString("city", "EARTH"),
        countryCode = json.optString("country_code", "XX"),
        verified = json.optBoolean("verified"),
        initials = json.optString("initials", "ON"),
    )

    private fun parseMessage(json: JSONObject) = OneMessage(
        id = json.getString("id"),
        text = json.getString("text"),
        status = runCatching { MessageStatus.valueOf(json.optString("status", "approved").uppercase()) }.getOrDefault(MessageStatus.REVIEWING),
        createdAtMillis = json.optLong("created_at_ms", System.currentTimeMillis()),
        timesDeployed = json.optInt("times_deployed"),
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun markDisconnected() {
        _world.value = _world.value.copy(connected = false)
    }

    private fun publishConnectionError(error: Throwable) {
        markDisconnected()
        _events.tryEmit(OneEvent.Error(error.userMessage()))
    }

    private fun Throwable.userMessage(): String = when (this) {
        is ApiException -> when {
            message?.contains("HANDLE_RESERVED") == true -> "That handle is protected. Choose an original alias."
            message?.contains("HANDLE_ALREADY_TAKEN") == true -> "That handle already belongs to someone."
            message?.contains("HANDLE_MUST_BE_3_TO_18_CHARACTERS") == true -> "Use 3 to 18 characters."
            else -> message ?: "ONE request failed."
        }
        else -> "Cannot reach ONE. Check your connection and backend configuration."
    }

    fun close() {
        scope.cancel()
    }

    private data class AuthSession(val accessToken: String, val refreshToken: String)
    private class ApiException(status: Int, message: String) : Exception("$message [$status]")

    companion object {
        fun isConfigured(): Boolean = BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

        private fun fallbackUser(id: String) = OneOwner(id, "@YOU", "EARTH", "XX", false, "YO")

        private fun placeholderHall(owner: OneOwner, message: OneMessage) = HallEntry(1, owner, message.text, 0, 0)

        private fun disconnectedWorld(): WorldState {
            val owner = OneOwner("system", "@ONE", "THE INTERNET", "XX", true, "1")
            val message = OneMessage("system", "TAKE THIS SCREEN.", MessageStatus.APPROVED, System.currentTimeMillis())
            val reign = Reign(
                id = "offline",
                owner = owner,
                message = message,
                startedAtMillis = System.currentTimeMillis(),
                protectedUntilMillis = 0,
                paidCredits = 0,
                openingPrice = 0,
                floorPrice = 0,
                palette = PaletteKey.ACID,
            )
            return WorldState(
                reign = reign,
                currentPrice = 0,
                appViews = 0,
                webViews = 0,
                liveWatchers = 0,
                credits = 0,
                userDailyReignSeconds = 0,
                userRetakesToday = 0,
                dailyCapSeconds = 1,
                messages = emptyList(),
                hall = listOf(placeholderHall(owner, message)),
                history = emptyList(),
                connected = false,
                demoMode = false,
                currentUserId = "",
            )
        }
    }
}
