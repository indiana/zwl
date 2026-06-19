package com.example.zwl.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class FireRiskResponse(
    val status: String,
    val riskLevel: Int
)

interface BdlFireApi {
    @GET("api/fire_hazard")
    suspend fun getFireHazard(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): FireRiskResponse
}
