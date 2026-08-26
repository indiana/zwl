package com.indiana.zwl.shared.data.remote

import com.indiana.zwl.shared.data.remote.model.OgcFeatureCollection
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class BdlOgcApi(private val client: HttpClient) {

    companion object {
        private const val BASE = "https://ogcapi.bdl.lasy.gov.pl/"
    }

    suspend fun getForestStands(
        collectionId: String,
        bbox: String,
        limit: Int = 500
    ): OgcFeatureCollection {
        return client.get("${BASE}collections/$collectionId/items") {
            parameter("f", "json")
            parameter("bbox", bbox)
            parameter("limit", limit)
            parameter("properties", "species_cd,spec_age,sub_area,forest_fun,stand_stru,site_type,prot_categ,rotat_age")
            parameter("skipGeometry", true)
        }.body()
    }

    suspend fun findNadlesnictwo(
        bbox: String,
        limit: Int = 1
    ): OgcFeatureCollection {
        return client.get("${BASE}collections/nadlesnictwa/items") {
            parameter("f", "json")
            parameter("bbox", bbox)
            parameter("limit", limit)
            parameter("properties", "inspectorate_name,region_cd")
            parameter("skipGeometry", true)
        }.body()
    }
}
