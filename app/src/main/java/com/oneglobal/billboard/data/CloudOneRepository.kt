package com.oneglobal.billboard.data

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.withContext
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
    private var hallRefreshAt = 0L
    private var cachedHallToday: List<HallEntry> = emptyList()
    private var cachedHallAllTime: List<HallEntry> = emptyList()
    private var hallAllTimeLive = false

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
                onPhase(ChallengePhase.SPENDING, "VERIFYING 1 ONE CREDIT")
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
        }.onFailure { error ->
            Log.e(LOG_TAG, "submit_message failed", error)
            _events.emit(OneEvent.MessageRejected(error.userMessage()))
        }
    }

    override suspend fun grantAdReward() {
        runCatching {
            val requestId = UUID.randomUUID().toString()
            val result = rpc("grant_ad_skip", JSONObject().put("p_request_id", requestId), authenticated = true)
            refreshWorld()
            if (result.optBoolean("ad_skip_available")) {
                _events.emit(OneEvent.AdSkipGranted(result.optInt("ad_skips_remaining_today", 0)))
            }
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override fun grantPurchasedCredits(amount: Int) {
        scope.launch {
            delay(700)
            runCatching { refreshWorld() }
            _events.emit(OneEvent.Error("Purchase received. The server is verifying your ONE Credits."))
        }
    }

    override fun reportCurrentMessage(reason: String) {
        scope.launch {
            runCatching { rpc("report_one", JSONObject().put("p_reason", reason), authenticated = true) }
                .onSuccess {
                    // The report itself is the confirmation. Refresh immediately so
                    // this user's live screen is masked without another action.
                    refreshWorld()
                    _events.emit(OneEvent.Error("Reported. Hidden for you."))
                }
                .onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
        }
    }

    override suspend fun blockCurrentOwner() {
        runCatching {
            rpc("block_current_owner", JSONObject(), authenticated = true)
            refreshWorld()
            _events.emit(OneEvent.Error("Blocked. Their content is now hidden from you."))
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override suspend fun unblockAll() {
        runCatching {
            val result = rpc("unblock_all", JSONObject(), authenticated = true)
            refreshWorld()
            _events.emit(OneEvent.Error("${result.optInt("unblocked")} blocked account(s) restored."))
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override suspend fun deleteAccount() {
        runCatching {
            rpc("delete_my_account", JSONObject(), authenticated = true)
            preferences.edit().clear().commit()
            ensureSession()
            rpc("ensure_profile", JSONObject(), authenticated = true)
            refreshWorld()
            _events.emit(OneEvent.AccountDeleted)
        }.onFailure { _events.emit(OneEvent.Error(it.userMessage())) }
    }

    override suspend fun updateProfile(city: String, countryCode: String) {
        rpc("update_profile_details", JSONObject().put("p_city", city).put("p_country_code", countryCode), true)
        hallRefreshAt = 0L
        refreshWorld()
    }

    override suspend fun updatePhoto(jpeg: ByteArray?) {
        require(jpeg == null || jpeg.size <= 75_000) { "Please choose a smaller photo." }
        val encoded = jpeg?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        rpc("set_profile_photo", JSONObject().put("p_photo", encoded ?: JSONObject.NULL), true)
        hallRefreshAt = 0L
        refreshWorld()
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

    override suspend fun createRecoveryCode() {
        runCatching {
            val result = rpc("create_recovery_code", JSONObject(), authenticated = true)
            _events.emit(OneEvent.RecoveryCodeCreated(result.getString("recovery_code")))
        }.onFailure { _events.emit(OneEvent.IdentityRejected(it.userMessage())) }
    }

    override suspend fun recoverIdentity(handle: String, code: String) {
        runCatching {
            val result = rpc(
                "recover_identity",
                JSONObject().put("p_handle", handle).put("p_code", code),
                authenticated = true,
            )
            refreshWorld()
            _events.emit(OneEvent.IdentityRecovered(result.getString("handle")))
        }.onFailure { _events.emit(OneEvent.IdentityRejected(it.userMessage())) }
    }

    override suspend fun reclaimUnclaimedHandle(handle: String) {
        runCatching {
            val result = rpc(
                "reclaim_unclaimed_handle",
                JSONObject().put("p_handle", handle),
                authenticated = true,
            )
            refreshWorld()
            _events.emit(OneEvent.IdentityRecovered(result.getString("handle")))
        }.onFailure { _events.emit(OneEvent.IdentityRejected(it.userMessage())) }
    }

    override suspend fun submitFeedback(category: String, text: String) {
        runCatching {
            rpc(
                "submit_feedback",
                JSONObject().put("p_category", category).put("p_text", text),
                authenticated = true,
            )
            _events.emit(OneEvent.FeedbackSubmitted)
        }.onFailure { error ->
            _events.emit(OneEvent.Error(error.userMessage()))
        }
    }

    private suspend fun refreshWorld() {
        val before = _world.value
        var next = parseWorld(rpc("get_one_state", JSONObject(), authenticated = true))
        val now = System.currentTimeMillis()
        if (now - hallRefreshAt >= 15_000L) {
            runCatching {
                parseHall(rpc("get_hall", JSONObject().put("p_period", "today").put("p_limit", 100), authenticated = true))
            }.getOrNull()?.let { cachedHallToday = it }
            runCatching {
                parseHall(rpc("get_hall", JSONObject().put("p_period", "all_time").put("p_limit", 100), authenticated = true))
            }.getOrNull()?.let {
                cachedHallAllTime = it
                hallAllTimeLive = true
            }
            hallRefreshAt = now
        }
        next = next.copy(
            hallToday = cachedHallToday,
            hallAllTime = cachedHallAllTime,
            hallAllTimeLive = hallAllTimeLive,
        )
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

    override suspend fun googleIdentity(idToken: String, nonce: String, restore: Boolean) = withContext(Dispatchers.IO) {
        // The restore path is a separate, explicitly confirmed action, never a linking fallback.
        val current = if (!restore) refreshSession() else null
        authMutex.withLock {
            val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=id_token", "POST", current?.accessToken)
            val body = JSONObject().put("provider", "google").put("id_token", idToken)
                .put("nonce", nonce).put("link_identity", !restore)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val payload = connection.readJson()
            if (connection.responseCode !in 200..299) {
                val code = payload.optString("error_code", payload.optString("error", "unknown"))
                val detail = when (code) {
                    "manual_linking_disabled" -> "Account linking is disabled on the server. Please contact ONE support."
                    "identity_already_exists" -> "This Google account already protects another ONE identity. Use Restore to open that account."
                    "bad_jwt", "bad_oauth_state", "validation_failed" -> "Google could not be verified. Please retry; if it continues, contact ONE support."
                    else -> "Google connection failed (HTTP ${connection.responseCode}, $code). Please retry."
                }
                throw IllegalStateException("$detail Your current handle is unchanged.")
            }
            val returnedId = payload.getJSONObject("user").getString("id")
            check(restore || returnedId == _world.value.currentUserId) { "Identity mismatch. Existing session retained." }
            persistSession(payload)
        }
        cachedHallToday = emptyList()
        cachedHallAllTime = emptyList()
        hallRefreshAt = 0L
        refreshWorld()
    }

    override suspend fun isGoogleIdentityLinked(): Boolean = withContext(Dispatchers.IO) {
        val token = ensureSession().accessToken
        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/user", "GET", token)
        val payload = connection.readJson()
        if (connection.responseCode !in 200..299) return@withContext false
        val identities = payload.optJSONArray("identities") ?: return@withContext false
        (0 until identities.length()).any { index ->
            identities.optJSONObject(index)?.optString("provider") == "google"
        }
    }

    private suspend fun ensureSession(): AuthSession = authMutex.withLock {
        val storedAccess = preferences.getString("access_token", null)
        val storedRefresh = preferences.getString("refresh_token", null)
        if (!storedAccess.isNullOrBlank()) return AuthSession(storedAccess, storedRefresh.orEmpty())
        if (!storedRefresh.isNullOrBlank()) return@withLock refreshSessionLocked(storedRefresh)
        check(preferences.getString("user_id", null).isNullOrBlank()) {
            "Saved identity needs recovery. ONE will not replace it with a new account."
        }

        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/signup", "POST", null)
        connection.outputStream.use { it.write("{}".toByteArray()) }
        val payload = connection.readJson()
        if (connection.responseCode !in 200..299) throw ApiException(connection.responseCode, payload.optString("msg", "Anonymous sign-in failed."))
        return persistSession(payload)
    }

    private suspend fun refreshSession(): AuthSession = authMutex.withLock {
        val refresh = preferences.getString("refresh_token", null)
            ?: throw ApiException(401, "Missing refresh token.")
        refreshSessionLocked(refresh)
    }

    private fun refreshSessionLocked(refresh: String): AuthSession {
        val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token", "POST", null)
        connection.outputStream.use { stream ->
            stream.write(JSONObject().put("refresh_token", refresh).toString().toByteArray())
        }
        val payload = connection.readJson()
        if (connection.responseCode !in 200..299) {
            // Never turn a temporary backend failure into an identity loss. Only remove
            // a stored session when Supabase explicitly rejects the refresh credential.
            // Keep the identity even when refresh is rejected. Never silently sign up again.
            throw ApiException(connection.responseCode, "Session refresh failed.")
        }
        return persistSession(payload)
    }

    private fun persistSession(payload: JSONObject): AuthSession {
        val session = AuthSession(payload.getString("access_token"), payload.optString("refresh_token"))
        // A synchronous commit is intentional: this identity must survive a process kill
        // immediately after sign-in and every normal Play Store update.
        check(
            preferences.edit()
                .putString("access_token", session.accessToken)
                .putString("refresh_token", session.refreshToken)
                .putString("user_id", payload.optJSONObject("user")?.optString("id") ?: preferences.getString("user_id", null))
                .commit(),
        ) { "Unable to persist anonymous ONE session." }
        return session
    }

    private suspend fun rpc(name: String, body: JSONObject, authenticated: Boolean, retry: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            val token = if (authenticated) ensureSession().accessToken else BuildConfig.SUPABASE_ANON_KEY
            val connection = open("${BuildConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/rpc/$name", "POST", token)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val payload = connection.readJson()
            if (connection.responseCode == 401 && authenticated && retry) {
                refreshSession()
                return@withContext rpc(name, body, authenticated, retry = false)
            }
            if (connection.responseCode !in 200..299) {
                throw ApiException(connection.responseCode, payload.optString("message", payload.optString("hint", "ONE request failed.")))
            }
            payload
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
        }

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
            currentContentBlocked = json.optBoolean("current_content_blocked"),
            blockedCount = json.optInt("blocked_count"),
            reactionCounts = json.optJSONObject("reaction_counts")?.let { counts -> counts.keys().asSequence().associateWith { counts.optInt(it) } },
            myReactions = json.optJSONArray("my_reactions")?.let { list -> (0 until list.length()).map { list.getString(it) }.toSet() } ?: emptySet(),
            adSkipAvailable = json.optBoolean("ad_skip_available"),
            adSkipsRemainingToday = json.optInt("ad_skips_remaining_today", 3),
            userTakeovers = json.optJSONObject("user_stats")?.optInt("takeovers"),
            userLongestReign = json.optJSONObject("user_stats")?.optInt("longest_reign_seconds"),
            userVerifiedViews = json.optJSONObject("user_stats")?.optInt("verified_views"),
        )
    }

    private fun parseOwner(json: JSONObject) = OneOwner(
        id = json.optString("id"),
        handle = json.optString("handle", "@ONE"),
        city = json.optString("city", "EARTH"),
        countryCode = json.optString("country_code", "XX"),
        verified = json.optBoolean("verified"),
        initials = json.optString("initials", "ON"),
        photoVersion = json.optString("photo_version").takeIf { it.isNotBlank() && it != "null" },
    )

    private fun parseMessage(json: JSONObject) = OneMessage(
        id = json.getString("id"),
        text = json.getString("text"),
        status = runCatching { MessageStatus.valueOf(json.optString("status", "approved").uppercase()) }.getOrDefault(MessageStatus.REVIEWING),
        createdAtMillis = json.optLong("created_at_ms", System.currentTimeMillis()),
        timesDeployed = json.optInt("times_deployed"),
    )

    private fun parseHall(json: JSONObject): List<HallEntry> =
        json.optJSONArray("entries").objects().mapIndexed { index, item ->
            HallEntry(
                rank = item.optInt("rank", index + 1),
                owner = OneOwner(
                    id = item.optString("owner_id"),
                    handle = item.optString("owner", "@ONE"),
                    city = item.optString("city", "EARTH"),
                    countryCode = item.optString("country_code", "XX"),
                    verified = item.optBoolean("verified"),
                    initials = item.optString("initials", "ON"),
                    photoVersion = item.optString("photo_version").takeIf { it.isNotBlank() && it != "null" },
                ),
                message = item.optString("message"),
                reignSeconds = item.optInt("reign_seconds"),
                verifiedViews = item.optInt("verified_views"),
                takeovers = if (item.has("takeovers")) item.optInt("takeovers") else null,
            )
        }

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
            message?.contains("RECOVERY_CODE_ALREADY_CREATED") == true -> "A recovery code already exists. Use the one you saved."
            message?.contains("RECOVERY_CODE_INVALID") == true -> "That recovery code does not match this handle."
            message?.contains("RECOVERY_NOT_AVAILABLE") == true -> "This identity cannot be recovered with that code."
            message?.contains("HANDLE_MUST_BE_3_TO_18_CHARACTERS") == true -> "Use 3 to 18 characters."
            message?.contains("CANNOT_BLOCK_YOURSELF") == true -> "You cannot block your own live reign."
            message?.contains("CANNOT_BLOCK_ONE") == true -> "The system screen cannot be blocked."
            message?.contains("ACCOUNT_SUSPENDED") == true -> "Taking ONE is temporarily unavailable for this account."
            message?.contains("REPORT_RATE_LIMIT") == true -> "You have sent a lot of reports recently. Please try again later."
            message?.contains("CANNOT_REPORT_YOURSELF") == true -> "You cannot report your own live message."
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
        private const val LOG_TAG = "ONE_NETWORK"

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
