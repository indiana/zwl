package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.OfflineAreaNames
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineAreaNamesTest {

    private val dayMillis = 86_400_000L

    @Test
    fun `autoName formats day month and time in local offset`() {
        // Epoch 0 = 1970-01-01 00:00 UTC.
        assertEquals("Obszar 01.01 00:00", OfflineAreaNames.autoName(0L, 0))
        // 06:30 UTC with +120 min offset -> local 08:30, same calendar day.
        assertEquals("Obszar 01.01 08:30", OfflineAreaNames.autoName(23_400_000L, 120))
    }

    @Test
    fun `ageLabel today yesterday and days`() {
        assertEquals("dziś, 00:00", OfflineAreaNames.ageLabel(0L, 0L, 0))
        assertEquals("wczoraj", OfflineAreaNames.ageLabel(0L, dayMillis, 0))
        assertEquals("4 dni temu", OfflineAreaNames.ageLabel(0L, 4 * dayMillis, 0))
        assertEquals("01.01.1970", OfflineAreaNames.ageLabel(0L, 100 * dayMillis, 0))
    }

    @Test
    fun `ageLabel follows local calendar not UTC`() {
        // Downloaded at 23:00 UTC (day 0), now 01:00 UTC (day 1). With a
        // +120 min offset both fall on the same local day -> "dziś".
        val downloaded = 23 * 3_600_000L
        val now = dayMillis + 3_600_000L
        assertEquals("dziś, 01:00", OfflineAreaNames.ageLabel(downloaded, now, 120))
        // Without the offset the same pair would cross the UTC boundary...
        assertEquals("wczoraj", OfflineAreaNames.ageLabel(downloaded, now, 0))
    }

    @Test
    fun `ageLabel treats future timestamps as today`() {
        val future = dayMillis + 12 * 3_600_000L
        assertEquals("dziś, 12:00", OfflineAreaNames.ageLabel(future, 0L, 0))
    }
}
