package com.indiana.zwl.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaFormatTest {

    @Test
    fun `formatAreaHa groups digits with exactly 2 decimals`() {
        assertEquals("123.46", formatAreaHa(1234567.891))
        assertEquals("1 234 567.89", formatAreaHa(12345678901.2))
    }

    @Test
    fun `formatAreaSqM groups digits without decimals`() {
        assertEquals("12 345 678 901", formatAreaSqM(12345678901.0))
        assertEquals("999", formatAreaSqM(999.4))
        assertEquals("1 234 568", formatAreaSqM(1234567.891))
    }

    @Test
    fun `formatArea handles zero and small values`() {
        assertEquals("0.00", formatAreaHa(0.0))
        assertEquals("0", formatAreaSqM(0.0))
        assertEquals("0.00", formatAreaHa(0.004))   // rounds down to 0.00
        assertEquals("1", formatAreaSqM(0.6))       // rounds up to whole
    }

    @Test
    fun `formatArea uses regular space as grouping separator`() {
        val ha = formatAreaHa(12345678901.2)
        assertEquals("1 234 567.89", ha)
        // U+0020 only — no NBSP (U+00A0) or narrow NBSP (U+202F) from locale defaults
        assertTrue(ha.none { it == '\u00A0' || it == '\u202F' })
    }
}
