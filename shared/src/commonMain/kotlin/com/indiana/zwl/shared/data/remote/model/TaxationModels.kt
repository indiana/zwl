package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Esri/JSON query result of the public `WMS_BDL` map service (wydzielenia
 * PGL LP layer 5 / wydzielenia poza PGL LP layer 6). Only the attributes the
 * taxation-description lookup needs are mapped.
 */
@Serializable
data class EsriQueryAttributes(
    @SerialName("arodes_int_num") val arodesIntNum: Long? = null,
    @SerialName("adress_forest") val adressForest: String? = null,
    @SerialName("a_year") val aYear: Int? = null,
    @SerialName("owner_cat_name") val ownerCatName: String? = null
)

@Serializable
data class EsriQueryFeature(
    val attributes: EsriQueryAttributes? = null
)

@Serializable
data class EsriQueryResult(
    val features: List<EsriQueryFeature>? = null
)

/**
 * Response of the BDL portal taxation-description endpoint
 * (`BULiGL.BDL.Reports/Map/StandDescriptionData`). Values arrive as
 * "CODE:Polish name" strings, e.g. `"soil_subtype_cd": "RDb:Gleby rdzawe
 * bielicowe"`, `"veg_cover_cd": "MSZC:mszysta-czernicowa"`.
 */
@Serializable
data class StandDescriptionResponse(
    val generalData: List<Map<String, String>>? = null,
    val generalData2: List<Map<String, String>>? = null
)
