package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.domain.model.SoilCover
import com.indiana.zwl.domain.model.TranslatedCode
import com.indiana.zwl.shared.data.remote.model.EsriQueryResult
import com.indiana.zwl.shared.data.remote.model.StandDescriptionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/**
 * Public BDL endpoints providing the SILP taxation description of a forest
 * subarea (wydzielenie) — the same data the official mBDL app shows:
 * soil type, ground cover (pokrywa), moisture, degradation, plant community.
 *
 * Flow: resolve the wydzielenie under a point via the `WMS_BDL` map service
 * (layer 5 = PGL LP, layer 6 = outside PGL LP), then ask the portal's
 * StandDescriptionData endpoint for the decoded description.
 */
class BdlStandDescriptionApi(private val client: HttpClient) {

    data class SubareaRef(
        val arodesIntNum: Long,
        val adressForest: String,
        val aYear: Int,
        val jointOwnership: Boolean
    )

    suspend fun findSubarea(latitude: Double, longitude: Double): SubareaRef? {
        val pgl = queryLayer(PGL_LAYER, latitude, longitude).features
            ?.firstOrNull()?.attributes
        if (pgl?.arodesIntNum != null && !pgl.adressForest.isNullOrBlank() && pgl.aYear != null) {
            return SubareaRef(pgl.arodesIntNum, pgl.adressForest, pgl.aYear, jointOwnership = false)
        }
        val outside = queryLayer(OUTSIDE_PGL_LAYER, latitude, longitude).features
            ?.firstOrNull()?.attributes
        if (outside?.arodesIntNum != null && !outside.adressForest.isNullOrBlank() && outside.aYear != null) {
            return SubareaRef(outside.arodesIntNum, outside.adressForest, outside.aYear, jointOwnership = true)
        }
        return null
    }

    suspend fun getSoilCover(subarea: SubareaRef): SoilCover {
        val response = client.get(STAND_DESCRIPTION_URL) {
            bdlHeaders()
            parameter("arodesIntNum", subarea.arodesIntNum)
            parameter("aYear", subarea.aYear)
            parameter("adressForest", subarea.adressForest)
            parameter("jointOwnership", subarea.jointOwnership)
            parameter("label", "soil")
        }.body<StandDescriptionResponse>()
        val soilType = response.generalData
            ?.firstOrNull()?.get("soil_subtype_cd")?.toTranslatedCode()
        val groundCover = response.generalData2
            ?.firstOrNull()?.get("veg_cover_cd")?.toTranslatedCode()
        return SoilCover(soilType, groundCover)
    }

    private suspend fun queryLayer(layer: Int, latitude: Double, longitude: Double): EsriQueryResult {
        val url = WMS_BDL_QUERY_URL.replace("%d", layer.toString())
        return client.get(url) {
            bdlHeaders()
            parameter("geometry", "$longitude,$latitude")
            parameter("geometryType", "esriGeometryPoint")
            parameter("inSR", 4326)
            parameter("spatialRel", "esriSpatialRelIntersects")
            parameter("outFields", "*")
            parameter("returnGeometry", false)
            parameter("f", "json")
        }.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bdlHeaders() {
        headers {
            append("Referer", BDL_REFERER)
            append(HttpHeaders.UserAgent, BDL_USER_AGENT)
        }
    }

    companion object {
        private const val WMS_BDL_QUERY_URL =
            "https://mapserver.bdl.lasy.gov.pl/arcgis/rest/services/WMS_BDL/MapServer/%d/query"
        private const val STAND_DESCRIPTION_URL =
            "https://www.bdl.lasy.gov.pl/portal/BULiGL.BDL.Reports/Map/StandDescriptionData"
        private const val PGL_LAYER = 5
        private const val OUTSIDE_PGL_LAYER = 6

        // The BDL map/portal endpoints reject requests without a browser-like
        // Referer (nginx 403 otherwise).
        private const val BDL_REFERER = "https://www.bdl.lasy.gov.pl/portal/mapy"
        private const val BDL_USER_AGENT = "Mozilla/5.0"
    }
}

/**
 * `"CODE:Polish name"` → [TranslatedCode]. Empty/blank or code-less values
 * (":") map to null.
 */
private fun String.toTranslatedCode(): TranslatedCode? {
    val value = trim()
    if (value.isEmpty() || value == "-") return null
    val separator = value.indexOf(':')
    if (separator < 0) return TranslatedCode(code = value, name = value)
    if (separator == 0) return null
    return TranslatedCode(
        code = value.substring(0, separator).trim(),
        name = value.substring(separator + 1).trim()
    )
}
