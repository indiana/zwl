package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class OgcFeatureCollection(
    val type: String,
    val features: List<OgcFeature>,
    val numberMatched: Int? = null,
    val numberReturned: Int? = null,
    val links: List<OgcLink>? = null
)

@Serializable
data class OgcFeature(
    val type: String,
    val id: Int? = null,
    val properties: OgcStandProperties? = null,
    val geometry: GeoJsonGeometry? = null
)

@Serializable
data class OgcStandProperties(
    val species_cd: String? = null,
    val spec_age: Int? = null,
    val sub_area: Double? = null,
    val forest_fun: String? = null,
    val stand_stru: String? = null,
    val site_type: String? = null,
    val prot_categ: String? = null,
    val rotat_age: Int? = null
)

@Serializable
data class OgcLink(
    val rel: String? = null,
    val type: String? = null,
    val title: String? = null,
    val href: String? = null
)
