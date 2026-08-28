package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val properties: Map<String, JsonElement?>? = null,
    val geometry: GeoJsonGeometry? = null
) {
    val standProperties: OgcStandProperties?
        get() = properties?.let { props ->
            OgcStandProperties(
                species_cd = props["species_cd"]?.toString()?.trim('"'),
                spec_age = props["spec_age"]?.toString()?.trim('"')?.toIntOrNull(),
                sub_area = props["sub_area"]?.toString()?.trim('"')?.toDoubleOrNull(),
                forest_fun = props["forest_fun"]?.toString()?.trim('"'),
                stand_stru = props["stand_stru"]?.toString()?.trim('"'),
                site_type = props["site_type"]?.toString()?.trim('"'),
                prot_categ = props["prot_categ"]?.toString()?.trim('"'),
                rotat_age = props["rotat_age"]?.toString()?.trim('"')?.toIntOrNull()
            )
        }
}

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
