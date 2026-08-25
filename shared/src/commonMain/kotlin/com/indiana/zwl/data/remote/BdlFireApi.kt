package com.indiana.zwl.data.remote

import com.indiana.zwl.data.remote.model.FireRiskGeoJson

interface BdlFireApi {
    suspend fun getFireHazard(geometry: String): FireRiskGeoJson
}
