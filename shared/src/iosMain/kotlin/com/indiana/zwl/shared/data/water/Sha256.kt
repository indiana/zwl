@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.indiana.zwl.shared.data.water

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual object Sha256 {
    actual fun hex(bytes: ByteArray): String {
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        bytes.usePinned { data ->
            digest.usePinned { output ->
                CC_SHA256(
                    if (bytes.isEmpty()) null else data.addressOf(0),
                    bytes.size.toUInt(),
                    output.addressOf(0)
                )
            }
        }
        return digest.joinToString(separator = "") { byte ->
            byte.toString(16).padStart(2, '0')
        }
    }
}
