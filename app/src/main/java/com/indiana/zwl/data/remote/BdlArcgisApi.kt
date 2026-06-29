package com.indiana.zwl.data.remote

import com.indiana.zwl.data.remote.model.GeoJsonCollection
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface BdlArcgisApi {
    @GET("arcgis/rest/services/WFS_BDL_mapa_turystyczna/MapServer/76/query")
    suspend fun getZanocujWLesieZones(
        @Query("where") where: String = "1=1",
        @Query("outFields") outFields: String = "*",
        @Query("maxAllowableOffset") maxAllowableOffset: Double = 0.00001,
        @Query("f") format: String = "geojson"
    ): GeoJsonCollection

    @GET("arcgis/rest/services/WFS_BDL_mapa_turystyczna/MapServer/{layerId}/query")
    suspend fun getTouristPoints(
        @Path("layerId") layerId: Int,
        @Query("where") where: String = "1=1",
        @Query("outFields") outFields: String = "tur_rec_pnt_cd,tur_obj_desc,nzw_ob",
        @Query("resultOffset") resultOffset: Int,
        @Query("resultRecordCount") resultRecordCount: Int = 2000,
        @Query("f") format: String = "geojson"
    ): GeoJsonCollection
}
