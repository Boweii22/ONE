package com.oneglobal.billboard

import com.oneglobal.billboard.data.AuctionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuctionRulesTest {
    @Test
    fun priceDecaysContinuouslyButNeverBelowFloor() {
        assertEquals(1_000, AuctionRules.decayedPrice(1_000, 100, 0))
        assertEquals(550, AuctionRules.decayedPrice(1_000, 100, 150_000))
        assertTrue(AuctionRules.decayedPrice(1_000, 100, Long.MAX_VALUE) >= 100)
    }

    @Test
    fun repeatOwnersPayEscalatingTakeoverCost() {
        assertEquals(401, AuctionRules.challengeCost(400, 0))
        assertTrue(AuctionRules.challengeCost(400, 2) > AuctionRules.challengeCost(400, 1))
    }

    @Test
    fun compositionRulesRejectDangerousFormatsEarly() {
        assertEquals("Links are not allowed in the founding season.", AuctionRules.validateMessage("visit https://example.com"))
        assertEquals("Phone numbers are not allowed.", AuctionRules.validateMessage("call +44 7700 900123"))
        assertNull(AuctionRules.validateMessage("MUM, I MADE IT TO THE WHOLE WORLD."))
    }
}
