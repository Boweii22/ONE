package com.oneglobal.billboard

import com.oneglobal.billboard.model.OneOwner
import com.oneglobal.billboard.ui.locationLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileLocationTest {
    private fun owner(city: String, country: String) = OneOwner("id", "@TEST", city, country, false, "TE")

    @Test fun placeholdersAreNeverShownAsRealLocations() {
        assertEquals("Country not shared", owner("EARTH", "XX").locationLabel())
        assertEquals("Country not shared", owner("", "").locationLabel())
        assertEquals("Country not shared", owner("HIDDEN", "XX").locationLabel())
    }

    @Test fun onlyProvidedValidLocationPartsAreShown() {
        assertEquals(com.oneglobal.billboard.ui.countryLabel("GB"), owner("London", "gb").locationLabel())
        assertEquals(com.oneglobal.billboard.ui.countryLabel("NG"), owner("", "NG").locationLabel())
        assertEquals("Country not shared", owner("Lagos", "XX").locationLabel())
    }
}
