package com.indiana.zwl.update

import com.indiana.zwl.shared.update.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    private val latest = WhatsNew.latestNote()!!

    @Test
    fun `fresh install shows the latest note`() {
        assertEquals(latest, WhatsNew.noteToShow(latest.version, null, false))
    }

    @Test
    fun `same version after seen stays silent`() {
        assertNull(WhatsNew.noteToShow(latest.version, latest.version, false))
    }

    @Test
    fun `upgrade shows the latest note`() {
        assertEquals(latest, WhatsNew.noteToShow(latest.version, "1.5", false))
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
        assertEquals(latest, WhatsNew.noteToShow("9.9", null, false))
    }

    @Test
    fun `seen garbage version still shows the note`() {
        assertEquals(latest, WhatsNew.noteToShow(latest.version, "debug-local", false))
    }

    @Test
    fun `older installed version shows its own note`() {
        val note = WhatsNew.noteFor("1.6")!!
        assertEquals("1.6", note.version)
        assertEquals(note, WhatsNew.noteToShow("1.6", null, false))
    }

    @Test
    fun `debug always shows the latest note`() {
        assertEquals(latest, WhatsNew.noteToShow(latest.version, latest.version, true))
        assertEquals(latest, WhatsNew.noteToShow("0.9", "9.9", true))
    }

    @Test
    fun `shipped note has version and non empty items`() {
        assertTrue(latest.version.isNotBlank())
        assertTrue(latest.items.isNotEmpty())
        assertTrue(latest.items.all { it.isNotBlank() })
    }
}
