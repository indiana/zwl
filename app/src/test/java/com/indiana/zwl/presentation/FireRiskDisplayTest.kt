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
    fun `fireRiskStatusText unifies unknown and offline-no-cache levels`() {
        assertEquals(fireRiskStatusText(-2), fireRiskStatusText(-1))
        assertEquals(fireRiskStatusText(-2), fireRiskStatusText(99))     // any unexpected value
        assertTrue(fireRiskStatusText(-2).contains("Brak danych"))
        assertFalse(fireRiskStatusText(-2).contains("WARUNKOWO"))
        assertFalse(fireRiskStatusText(-2).contains("Nieznany"))         // -2 wording aligned with else
    }

    @Test
    fun `shouldPulse requires active tab and enabled animations`() {
        assertTrue(shouldPulse(isActive = true, animatorDurationScale = 1f))
        assertTrue(shouldPulse(isActive = true, animatorDurationScale = 0.5f))
        assertFalse(shouldPulse(isActive = false, animatorDurationScale = 1f))   // hidden tab
        assertFalse(shouldPulse(isActive = true, animatorDurationScale = 0f))    // remove animations
    }
}
