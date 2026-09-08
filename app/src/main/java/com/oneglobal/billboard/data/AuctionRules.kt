package com.oneglobal.billboard.data

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

object AuctionRules {
    const val PROTECTED_SECONDS = 12
    const val PRODUCTION_PROTECTED_SECONDS = 60
    const val DAILY_REIGN_CAP_SECONDS = 20 * 60
    const val MESSAGE_LIMIT = 80
    const val HANDLE_MIN = 3
    const val HANDLE_MAX = 18
    private val reservedHandles = setOf(
        "ONE", "ADMIN", "ADMINISTRATOR", "MOD", "MODERATOR", "SUPPORT", "OFFICIAL",
        "REVENUECAT", "SUPABASE", "ONESIGNAL", "GOOGLE", "GOOGLEPLAY", "ANDROID",
        "MICROSOFT", "APPLE", "META", "FACEBOOK", "INSTAGRAM", "WHATSAPP", "OPENAI",
        "CHATGPT", "TWITTER", "TIKTOK", "YOUTUBE", "SPACEX", "TESLA", "ELONMUSK",
        "ELON_MUSK", "MRBEAST", "NIKE", "ADIDAS",
    )

    fun remainingDailySeconds(usedSeconds: Int): Int =
        (DAILY_REIGN_CAP_SECONDS - usedSeconds).coerceAtLeast(0)

    // A handful of common TLDs for bare-domain detection (e.g. "find me at nova.com").
    // Deliberately not exhaustive — this is a heuristic pre-filter, not the only check.
    private val bareDomainRegex = Regex(
        "\\b[a-z0-9-]+\\.(com|net|org|io|co|me|app|dev|xyz|info|biz|one|gg|tv|link)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val emailRegex = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE)
    private val obviousBlocklist = listOf("kill yourself", "terrorist threat", "racial slur")

    fun validateMessage(raw: String, regionHint: String = Locale.getDefault().country): String? {
        val text = raw.trim()
        if (text.isBlank()) return "Say something first."
        if (text.length > MESSAGE_LIMIT) return "Keep it under $MESSAGE_LIMIT characters."
        if (emailRegex.containsMatchIn(text)) {
            return "That looks like an email address."
        }
        if (Regex("https?://|www\\.", RegexOption.IGNORE_CASE).containsMatchIn(text) || bareDomainRegex.containsMatchIn(text)) {
            return "That looks like a link."
        }
        if (looksLikePhoneNumber(text, regionHint)) {
            return "That looks like a phone number."
        }
        if (obviousBlocklist.any { text.contains(it, ignoreCase = true) }) {
            return "That looks like it violates the ONE community rules."
        }
        return null
    }

    /**
     * @handles are references to other players, not contact routes — ONE is built around
     * rivalry between named players, so they're explicitly never treated as phone numbers
     * even though they can contain digits (e.g. "@PLAYER_9B2CD7").
     */
    private fun looksLikePhoneNumber(text: String, regionHint: String): Boolean {
        val withoutHandles = Regex("@[A-Z0-9_]+", RegexOption.IGNORE_CASE).replace(text, " ")
        return runCatching {
            // POSSIBLE (not VALID) on purpose: this is a contact-info filter, not a carrier
            // lookup. A number-shaped sequence should be blocked even if it isn't currently
            // an allocated/issued number (e.g. +44 7700 900123, the UK's fictional TV number).
            // POSSIBLE alone is too loose on its own, though — it treats a bare 9-digit run as
            // a possible number in some regions. Require either an explicit '+' (unambiguous
            // international format) or enough digits to resemble a real national number.
            PhoneNumberUtil.getInstance()
                .findNumbers(withoutHandles, regionHint.ifBlank { "US" }, PhoneNumberUtil.Leniency.POSSIBLE, Long.MAX_VALUE)
                .any { match ->
                    val raw = match.rawString()
                    raw.contains('+') || raw.count { it.isDigit() } >= 10
                }
        }.getOrDefault(false)
    }

    fun normalizeHandle(raw: String): String =
        raw.trim().removePrefix("@").uppercase()

    fun validateHandle(raw: String): String? {
        val handle = normalizeHandle(raw)
        if (handle.isBlank()) return "Choose a handle or stay anonymous."
        if (handle.length !in HANDLE_MIN..HANDLE_MAX) {
            return "Use $HANDLE_MIN to $HANDLE_MAX characters."
        }
        if (!Regex("[A-Z0-9_]+").matches(handle)) {
            return "Use only letters, numbers, and underscores."
        }
        if (handle in reservedHandles) return "That handle is protected. Choose an original alias."
        return null
    }
}
