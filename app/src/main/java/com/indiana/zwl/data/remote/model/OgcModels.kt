package com.indiana.zwl.data.remote.model

data class OgcFeatureCollection(
    val type: String,
    val features: List<OgcFeature>,
    val numberMatched: Int?,
    val numberReturned: Int?,
    val links: List<OgcLink>?
)

data class OgcFeature(
    val type: String,
    val id: Int?,
    val properties: Map<String, Any?>?,
    val geometry: GeoJsonGeometry?
) {
    val standProperties: OgcStandProperties?
        get() = properties?.let {
            com.google.gson.Gson().fromJson(
                com.google.gson.Gson().toJson(it),
                OgcStandProperties::class.java
            )
        }
}

data class OgcStandProperties(
    val species_cd: String?,
    val spec_age: Int?,
    val sub_area: Double?,
    val forest_fun: String?,
    val stand_stru: String?,
    val site_type: String?,
    val prot_categ: String?,
    val rotat_age: Int?,
    val nazwa: String?
)

data class OgcLink(
    val rel: String?,
    val type: String?,
    val title: String?,
    val href: String?
)
