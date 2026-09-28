package com.indiana.zwl.shared.data.water

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `hashes the empty byte array`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(ByteArray(0))
        )
    }

    @Test
    fun `hashes a known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray())
        )
    }
}
