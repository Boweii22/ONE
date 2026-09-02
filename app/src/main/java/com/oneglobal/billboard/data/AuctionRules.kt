package com.oneglobal.billboard.data

import kotlin.math.pow
import kotlin.math.roundToInt

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

    fun decayedPrice(
        openingPrice: Int,
        floorPrice: Int,
        elapsedMillis: Long,
        halfLifeMillis: Long = 150_000L,
    ): Int {
        if (openingPrice <= floorPrice) return floorPrice
        val periods = elapsedMillis.coerceAtLeast(0L).toDouble() / halfLifeMillis.toDouble()
        val remaining = .5.pow(periods)
        return (floorPrice + (openingPrice - floorPrice) * remaining)
            .roundToInt()
            .coerceIn(floorPrice, openingPrice)
    }

    fun retakeMultiplier(retakesToday: Int): Double =
        1.0 + retakesToday.coerceAtLeast(0) * .35

    fun challengeCost(currentPrice: Int, retakesToday: Int): Int =
        ((currentPrice + 1) * retakeMultiplier(retakesToday)).roundToInt()

    fun openingPriceAfterTakeover(paidCredits: Int): Int =
        (paidCredits * 1.18).roundToInt().coerceAtLeast(paidCredits + 12)

    fun remainingDailySeconds(usedSeconds: Int): Int =
        (DAILY_REIGN_CAP_SECONDS - usedSeconds).coerceAtLeast(0)

    fun validateMessage(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return "Say something first."
        if (text.length > MESSAGE_LIMIT) return "Keep it under $MESSAGE_LIMIT characters."
        if (Regex("https?://|www\\.", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "Links are not allowed in the founding season."
        }
        if (Regex("(?:\\+?\\d[\\s().-]*){8,}").containsMatchIn(text)) {
            return "Phone numbers are not allowed."
        }
        val blocked = listOf("kill yourself", "terrorist threat", "racial slur")
        if (blocked.any { text.contains(it, ignoreCase = true) }) {
            return "This message violates the ONE community rules."
        }
        return null
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
