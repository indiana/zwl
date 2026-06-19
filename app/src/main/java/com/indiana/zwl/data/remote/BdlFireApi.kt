package com.indiana.zwl.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class FireRiskGeoJson(
    val features: List<FireRiskFeature>
)

data class FireRiskFeature(
    val properties: FireRiskProperties
)

data class FireRiskProperties(
    val kod: Int?,
    val opis: String?
)

interface BdlFireApi {
    @GET("arcgis/rest/services/WMS_zagrozenie_pozarowe_w_lasach/MapServer/0/query")
    suspend fun getFireHazard(
        @Query("geometry") geometry: String,
        @Query("geometryType") geometryType: String = "esriGeometryPoint",
        @Query("inSR") inSR: Int = 4326,
        @Query("spatialRel") spatialRel: String = "esriSpatialRelIntersects",
        @Query("outFields") outFields: String = "kod,opis",
        @Query("f") format: String = "geojson"
    ): FireRiskGeoJson
}
