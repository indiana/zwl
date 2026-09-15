package com.indiana.zwl.shared.data.remote

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class RemoteErrorsTest {

    @Test
    fun `network io exceptions are transient`() {
        assertTrue(isTransientRemoteError(UnknownHostException("no dns")))
        assertTrue(isTransientRemoteError(IOException("socket closed")))
    }

    @Test
    fun `deserialization failures are transient`() {
        assertTrue(isTransientRemoteError(SerializationException("unexpected html body")))
    }

    @Test
    fun `wrapped causes are inspected`() {
        val wrapped = IllegalStateException("boom", IOException("underlying"))
        assertTrue(isTransientRemoteError(wrapped))
    }

    @Test
    fun `unrelated programming errors are not transient`() {
        assertFalse(isTransientRemoteError(IllegalArgumentException("bad argument")))
        assertFalse(isTransientRemoteError(null))
    }
}
