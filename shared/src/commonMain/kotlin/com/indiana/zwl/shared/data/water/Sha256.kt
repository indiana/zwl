package com.indiana.zwl.shared.data.water

expect object Sha256 {
    fun hex(bytes: ByteArray): String
}
