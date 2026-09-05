package com.oneglobal.billboard

import com.oneglobal.billboard.model.OneOwner
import com.oneglobal.billboard.ui.locationLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileLocationTest {
    private fun owner(city: String, country: String) = OneOwner("id", "@TEST", city, country, false, "TE")

    @Test fun placeholdersAreNeverShownAsRealLocations() {
        assertEquals("Location not shared", owner("EARTH", "XX").locationLabel())
        assertEquals("Location not shared", owner("", "").locationLabel())
        assertEquals("Location not shared", owner("HIDDEN", "XX").locationLabel())
    }

    @Test fun onlyProvidedValidLocationPartsAreShown() {
        assertEquals("London, GB", owner("London", "gb").locationLabel())
        assertEquals("NG", owner("", "NG").locationLabel())
        assertEquals("Lagos", owner("Lagos", "XX").locationLabel())
    }
}
