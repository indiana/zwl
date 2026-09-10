package com.indiana.zwl.update

import com.indiana.zwl.shared.update.UpdateTools
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateToolsTest {

    @Test
    fun `store patch newer`() {
        assertTrue(UpdateTools.isUpdateAvailable("1.18", "1.19"))
    }

    @Test
    fun `store minor newer`() {
        assertTrue(UpdateTools.isUpdateAvailable("1.9", "1.10"))
    }

    @Test
    fun `same version`() {
        assertFalse(UpdateTools.isUpdateAvailable("1.18", "1.18"))
    }

    @Test
    fun `store older`() {
        assertFalse(UpdateTools.isUpdateAvailable("1.18", "1.17"))
    }

    @Test
    fun `multi digit segment beats string compare`() {
        assertTrue(UpdateTools.isUpdateAvailable("1.2.9", "1.2.10"))
    }

    @Test
    fun `missing segments treated as zero`() {
        assertTrue(UpdateTools.isUpdateAvailable("1.2", "1.2.1"))
        assertFalse(UpdateTools.isUpdateAvailable("1.2.1", "1.2"))
    }

    @Test
    fun `garbage installed version`() {
        assertTrue(UpdateTools.isUpdateAvailable("debug-local", "1.1"))
    }

    @Test
    fun `garbage both versions`() {
        assertFalse(UpdateTools.isUpdateAvailable("dev", "test"))
    }

    @Test
    fun `whitespace and prefix tolerated`() {
        assertTrue(UpdateTools.isUpdateAvailable(" 1.18 ", "2.0"))
        assertFalse(UpdateTools.isUpdateAvailable("v1.18", "1.18"))
    }
}
