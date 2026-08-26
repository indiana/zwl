package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.shared.data.remote.model.GeoJsonCollection
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class BdlArcgisApi(private val client: HttpClient) {

    companion object {
        private const val BASE = "https://mapserver.bdl.lasy.gov.pl/"
    }

    suspend fun getZanocujWLesieZones(): String {
        return client.get("${BASE}arcgis/rest/services/WFS_BDL_mapa_turystyczna/MapServer/76/query") {
            parameter("where", "1=1")
            parameter("outFields", "link,nzw_ob")
            parameter("maxAllowableOffset", 0.0001)
            parameter("f", "geojson")
        }.body()
    }

    suspend fun getTouristPoints(
        layerId: Int,
        outFields: String = "tur_rec_pnt_cd,tur_obj_desc,nzw_ob",
        resultOffset: Int,
        resultRecordCount: Int = 2000
    ): GeoJsonCollection {
        return client.get("${BASE}arcgis/rest/services/WFS_BDL_mapa_turystyczna/MapServer/$layerId/query") {
            parameter("where", "1=1")
            parameter("outFields", outFields)
            parameter("resultOffset", resultOffset)
            parameter("resultRecordCount", resultRecordCount)
            parameter("f", "geojson")
        }.body()
    }

    suspend fun getForestBans(
        resultOffset: Int = 0,
        resultRecordCount: Int = 500
    ): GeoJsonCollection {
        return client.get("${BASE}arcgis/rest/services/WMS_zakazy_wstepu_do_lasu/MapServer/0/query") {
            parameter("where", "1=1")
            parameter("outFields", "objectid,kod_nadl,nazwa_nadl,nazwa_rdlp,lesnictwo,kod_lesn,kod,opis,data,data_koncowa,adr_lesny,adr_silp,kod_oddzialu,st_area(shape)")
            parameter("maxAllowableOffset", 0.0001)
            parameter("resultOffset", resultOffset)
            parameter("resultRecordCount", resultRecordCount)
            parameter("f", "geojson")
        }.body()
    }
}
