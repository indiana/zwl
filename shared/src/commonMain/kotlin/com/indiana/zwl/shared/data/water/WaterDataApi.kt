package com.indiana.zwl.shared.data.water

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class WaterDataApi(private val client: HttpClient) {

    suspend fun fetchManifest(epochDay: Long): WaterManifest =
        client.get("${WaterDataConfig.BASE_URL}${WaterDataConfig.MANIFEST_FILE}?ts=$epochDay").body()

    suspend fun downloadDataFile(fileName: String): ByteArray =
        client.get("${WaterDataConfig.BASE_URL}$fileName").body()
}
