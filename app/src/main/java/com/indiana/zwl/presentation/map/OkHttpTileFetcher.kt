package com.indiana.zwl.presentation.map

import com.indiana.zwl.shared.offline.TileFetchResult
import com.indiana.zwl.shared.offline.TileFetcher
import com.indiana.zwl.shared.offline.TileMath
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class OkHttpTileFetcher(private val client: OkHttpClient) : TileFetcher {

    override suspend fun fetch(x: Int, y: Int, z: Int): TileFetchResult {
        val url = TileMath.tileUrl(x, y, z)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "LegalnyBushcraft/1.0 (Android)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        TileFetchResult.Ok(bytes)
                    } else {
                        TileFetchResult.Empty
                    }
                } else {
                    TileFetchResult.Empty
                }
            }
        } catch (e: IOException) {
            TileFetchResult.NetworkError
        }
    }
}