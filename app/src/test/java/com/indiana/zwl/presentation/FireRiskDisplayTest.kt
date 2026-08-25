package com.indiana.zwl.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireRiskDisplayTest {

    @Test
    fun `fireRiskStatusText maps known levels`() {
        assertEquals("STOPNIEŃ 0 (Brak zagrożenia)", fireRiskStatusText(0))
        assertEquals("STOPNIEŃ 3 (BARDZO WYSOKIE)", fireRiskStatusText(3))
        assertEquals("STOPNIEŃ 0 (Brak - archiwalne offline)", fireRiskStatusText(10))
        assertEquals("STOPNIEŃ 3 (WYSOKIE - archiwalne offline)", fireRiskStatusText(13))
    }

    @Test
    fun `fireRiskStatusText distinguishes no-network from no-data`() {
        assertEquals("Brak połączenia", fireRiskStatusText(-1))
        assertEquals("Brak danych z serwisu", fireRiskStatusText(-2))
        assertTrue(fireRiskStatusText(-1).contains("połączenia"))
        assertTrue(fireRiskStatusText(-2).contains("danych"))
        assertFalse(fireRiskStatusText(-1) == fireRiskStatusText(-2))
    }

    @Test
    fun `fireRiskStatusText handles other unknown values`() {
        assertTrue(fireRiskStatusText(99).contains("Nieznany"))
        assertFalse(fireRiskStatusText(99).contains("WARUNKOWO"))
    }

    @Test
    fun `shouldPulse requires active tab and enabled animations`() {
        assertTrue(shouldPulse(isActive = true, animatorDurationScale = 1f))
        assertTrue(shouldPulse(isActive = true, animatorDurationScale = 0.5f))
        assertFalse(shouldPulse(isActive = false, animatorDurationScale = 1f))   // hidden tab
        assertFalse(shouldPulse(isActive = true, animatorDurationScale = 0f))    // remove animations
    }
}
