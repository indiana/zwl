package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.remote.BdlOgcApi
import com.indiana.zwl.domain.model.AgeClass
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.SpeciesEntry
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.util.RdlpMapper
import kotlinx.coroutines.CancellationException
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.io.WKTReader
import javax.inject.Inject

class GetForestStandUseCase @Inject constructor(
    private val ogcApi: BdlOgcApi
) {
    suspend operator fun invoke(zone: Zone): Result<ForestStandSummary> {
        return try {
            val reader = WKTReader()
            val geometry = reader.read(zone.geometryWkt)
            val envelope: Envelope = geometry.envelopeInternal

            val bbox = "${envelope.minX},${envelope.minY},${envelope.maxX},${envelope.maxY}"
            val centroid = geometry.centroid
            val centroidGeom = "${centroid.x},${centroid.y}"

            val regionResult = ogcApi.findNadlesnictwo(geom = centroidGeom)
            val regionCd = regionResult.features?.firstOrNull()?.properties?.get("region_cd") as? String

            if (regionCd == null) {
                return Result.failure(Exception("Nie udało się ustalić regionu RDLP."))
            }

            val collectionId = RdlpMapper.collectionForRegionCode(regionCd)
                ?: return Result.failure(Exception("Nieznany region RDLP: $regionCd"))

            val stands = ogcApi.getForestStands(collectionId = collectionId, bbox = bbox)
            val features = stands.features ?: emptyList()

            if (features.isEmpty()) {
                return Result.success(emptySummary())
            }

            var totalArea = 0.0
            val speciesAreaMap = mutableMapOf<String, Double>()
            var forestFunction: String? = null
            var standStructure: String? = null
            var siteType: String? = null
            var protectionCategory: String? = null
            var rotationAge: Int? = null

            for (feature in features) {
                val props = feature.properties ?: continue
                val area = props.sub_area ?: 0.0

                if (area > 0) {
                    totalArea += area
                    val speciesCode = props.species_cd
                    if (!speciesCode.isNullOrBlank()) {
                        speciesAreaMap[speciesCode] = (speciesAreaMap[speciesCode] ?: 0.0) + area
                    }
                }

                if (forestFunction == null && props.forest_fun != null) {
                    forestFunction = RdlpMapper.forestFunCodeToName(props.forest_fun)
                }
                if (standStructure == null && props.stand_stru != null) {
                    standStructure = RdlpMapper.standStruCodeToName(props.stand_stru)
                }
                if (siteType == null && props.site_type != null) {
                    siteType = RdlpMapper.siteTypeCodeToName(props.site_type)
                }
                if (protectionCategory == null && props.prot_categ != null) {
                    protectionCategory = RdlpMapper.protCategCodeToName(props.prot_categ)
                }
                if (rotationAge == null && props.rotat_age != null && props.rotat_age > 0) {
                    rotationAge = props.rotat_age
                }
            }

            val speciesBreakdown = speciesAreaMap.entries
                .sortedByDescending { it.value }
                .map { (code, area) ->
                    val percentage = if (totalArea > 0) (area / totalArea) * 100.0 else 0.0
                    SpeciesEntry(
                        speciesCode = code,
                        speciesName = RdlpMapper.speciesCodeToName(code),
                        percentage = Math.round(percentage * 10.0) / 10.0,
                        ageClass = AgeClass.BRAK_DANYCH
                    )
                }

            Result.success(
                ForestStandSummary(
                    speciesBreakdown = speciesBreakdown,
                    forestFunction = forestFunction,
                    standStructure = standStructure,
                    siteType = siteType,
                    protectionCategory = protectionCategory,
                    totalAreaHa = Math.round(totalArea * 100.0) / 100.0,
                    rotationAge = rotationAge
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun emptySummary(): ForestStandSummary {
        return ForestStandSummary(
            speciesBreakdown = emptyList(),
            forestFunction = null,
            standStructure = null,
            siteType = null,
            protectionCategory = null,
            totalAreaHa = 0.0,
            rotationAge = null
        )
    }
}
