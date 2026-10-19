package com.indiana.zwl.update

import com.indiana.zwl.shared.update.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    private val shipped = WhatsNew.latestNote()!!

    @Test
    fun `fresh install shows the note`() {
        assertEquals(shipped, WhatsNew.noteToShow("1.6", null, false))
    }

    @Test
    fun `same version after seen stays silent`() {
        assertNull(WhatsNew.noteToShow("1.6", "1.6", false))
    }

    @Test
    fun `upgrade shows the note`() {
        assertEquals(shipped, WhatsNew.noteToShow("1.6", "1.5", false))
    }

    @Test
    fun `downgrade stays silent`() {
        assertNull(WhatsNew.noteToShow("1.6", "1.7", false))
    }

    @Test
    fun `version older than every note stays silent`() {
        assertNull(WhatsNew.noteToShow("0.9", null, false))
    }

    @Test
    fun `falls back to newest note not newer than current`() {
        assertEquals(shipped, WhatsNew.noteToShow("9.9", null, false))
    }

    @Test
    fun `seen garbage version still shows the note`() {
        assertEquals(shipped, WhatsNew.noteToShow("1.6", "debug-local", false))
    }

    @Test
    fun `debug always shows the latest note`() {
        assertEquals(shipped, WhatsNew.noteToShow("1.6", "1.6", true))
        assertEquals(shipped, WhatsNew.noteToShow("0.9", "9.9", true))
    }

    @Test
    fun `shipped note has version and non empty items`() {
        assertTrue(shipped.version.isNotBlank())
        assertTrue(shipped.items.isNotEmpty())
        assertTrue(shipped.items.all { it.isNotBlank() })
    }
}
