package com.indiana.zwl.shared.data.remote

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ResponseException
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException

/**
 * True when [error] (or any of its causes) looks like a transient remote
 * failure: no connectivity, a server/proxy error status (4xx/5xx — e.g. the
 * BDL ArcGIS gateway answering 502 with an HTML body), or a response the
 * client cannot deserialize. Callers should fall back to the last cached
 * value in these cases instead of surfacing / logging an error.
 */
fun isTransientRemoteError(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 20) {
        when (current) {
            is ResponseException,
            is NoTransformationFoundException,
            is IOException,
            is SerializationException -> return true
        }
        current = current.cause
        depth++
    }
    return false
}
