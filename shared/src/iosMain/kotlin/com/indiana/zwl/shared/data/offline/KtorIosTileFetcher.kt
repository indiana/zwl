package com.indiana.zwl.shared.data.offline

import com.indiana.zwl.shared.offline.TileFetchResult
import com.indiana.zwl.shared.offline.TileFetcher
import com.indiana.zwl.shared.offline.TileMath
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException

class KtorIosTileFetcher(private val client: HttpClient) : TileFetcher {

    override suspend fun fetch(x: Int, y: Int, z: Int): TileFetchResult {
        return try {
            val bytes = client.get(TileMath.tileUrl(x, y, z)).body<ByteArray>()
            if (bytes.isEmpty()) TileFetchResult.Empty else TileFetchResult.Ok(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TileFetchResult.NetworkError
        }
    }
}