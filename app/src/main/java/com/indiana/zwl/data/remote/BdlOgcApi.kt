package com.indiana.zwl.data.remote

import com.indiana.zwl.data.remote.model.OgcFeatureCollection
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BdlOgcApi {
    @GET("collections/{collectionId}/items")
    suspend fun getForestStands(
        @Path("collectionId") collectionId: String,
        @Query("f") format: String = "json",
        @Query("bbox") bbox: String,
        @Query("limit") limit: Int = 500,
        @Query("properties") properties: String = "species_cd,spec_age,sub_area,forest_fun,stand_stru,site_type,prot_categ,rotat_age",
        @Query("skipGeometry") skipGeometry: Boolean = true
    ): OgcFeatureCollection

    @GET("collections/nadlesnictwa/items")
    suspend fun findNadlesnictwo(
        @Query("f") format: String = "json",
        @Query("bbox") bbox: String,
        @Query("limit") limit: Int = 1,
        @Query("properties") properties: String = "inspectorate_name,region_cd",
        @Query("skipGeometry") skipGeometry: Boolean = true
    ): OgcFeatureCollection
}
