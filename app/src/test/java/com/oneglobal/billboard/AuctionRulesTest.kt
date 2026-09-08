package com.oneglobal.billboard

import com.oneglobal.billboard.data.AuctionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuctionRulesTest {
    @Test
    fun compositionRulesRejectDangerousFormatsEarly() {
        assertEquals("That looks like a link.", AuctionRules.validateMessage("visit https://example.com"))
        assertEquals("That looks like a link.", AuctionRules.validateMessage("find me at nova.com"))
        assertEquals("That looks like an email address.", AuctionRules.validateMessage("email me at nova@example.com"))
        assertEquals("That looks like a phone number.", AuctionRules.validateMessage("call +44 7700 900123"))
        assertEquals("That looks like a phone number.", AuctionRules.validateMessage("call 07700 900123", regionHint = "GB"))
        assertNull(AuctionRules.validateMessage("MUM, I MADE IT TO THE WHOLE WORLD."))
    }

    @Test
    fun phoneDetectionDoesNotFalselyRejectOrdinaryNumbersOrHandles() {
        assertNull(AuctionRules.validateMessage("MY LUCKY NUMBER IS 123456789.", regionHint = "US"))
        assertNull(AuctionRules.validateMessage("TAKE IT FROM @PLAYER_9B2CD7.", regionHint = "US"))
        assertNull(AuctionRules.validateMessage("REIGN NUMBER 4829201 IS MINE.", regionHint = "US"))
    }

    @Test
    fun handlesAreAliasesNotLegalNames() {
        assertEquals("BOWEI", AuctionRules.normalizeHandle("@bowei"))
        assertNull(AuctionRules.validateHandle("BOWEI_22"))
        assertEquals("Use 3 to 18 characters.", AuctionRules.validateHandle("AB"))
        assertEquals("Use only letters, numbers, and underscores.", AuctionRules.validateHandle("Bowei Thompson"))
        assertEquals("That handle is protected. Choose an original alias.", AuctionRules.validateHandle("NIKE"))
    }
}
