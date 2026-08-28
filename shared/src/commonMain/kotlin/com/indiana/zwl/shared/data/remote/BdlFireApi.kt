package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.shared.data.remote.model.FireRiskGeoJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class BdlFireApi(private val client: HttpClient) {

    companion object {
        private const val BASE = "https://mapserver.bdl.lasy.gov.pl/"
    }

    suspend fun getFireHazard(geometry: String): FireRiskGeoJson {
        return client.get("${BASE}arcgis/rest/services/WMS_zagrozenie_pozarowe_w_lasach/MapServer/0/query") {
            parameter("geometry", geometry)
            parameter("geometryType", "esriGeometryPoint")
            parameter("inSR", 4326)
            parameter("spatialRel", "esriSpatialRelIntersects")
            parameter("outFields", "kod,opis")
            parameter("returnGeometry", false)
            parameter("f", "geojson")
        }.body()
    }
}
