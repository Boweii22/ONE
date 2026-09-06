package com.oneglobal.billboard

import com.oneglobal.billboard.data.AuctionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuctionRulesTest {
    @Test
    fun compositionRulesRejectDangerousFormatsEarly() {
        assertEquals("Links are not allowed in the founding season.", AuctionRules.validateMessage("visit https://example.com"))
        assertEquals("Phone numbers are not allowed.", AuctionRules.validateMessage("call +44 7700 900123"))
        assertNull(AuctionRules.validateMessage("MUM, I MADE IT TO THE WHOLE WORLD."))
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
