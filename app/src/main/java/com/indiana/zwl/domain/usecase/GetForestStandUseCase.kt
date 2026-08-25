package com.indiana.zwl.domain.usecase

import com.indiana.zwl.data.remote.BdlOgcApi
import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.SpeciesEntry
import com.indiana.zwl.domain.model.TranslatedCode
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
            val geometry = reader.read(zone.geometryWkt)!!
            val envelope: Envelope = geometry.getEnvelopeInternal()

            val bbox = "${envelope.getMinX()},${envelope.getMinY()},${envelope.getMaxX()},${envelope.getMaxY()}"
            val centroid = geometry.getCentroid()

            val regionResult = ogcApi.findNadlesnictwo(
                bbox = "${centroid.getX() - 0.01},${centroid.getY() - 0.01},${centroid.getX() + 0.01},${centroid.getY() + 0.01}"
            )
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
            val speciesAgeSumMap = mutableMapOf<String, Double>()
            val speciesAgeCountMap = mutableMapOf<String, Double>()
            var forestFunction: TranslatedCode? = null
            var standStructure: TranslatedCode? = null
            var siteType: TranslatedCode? = null
            var protectionCategory: TranslatedCode? = null
            var rotationAge: Int? = null

            for (feature in features) {
                val props = feature.standProperties ?: continue
                val area = props.sub_area ?: 0.0

                if (area > 0) {
                    totalArea += area
                    val speciesCode = props.species_cd
                    if (!speciesCode.isNullOrBlank()) {
                        speciesAreaMap[speciesCode] = (speciesAreaMap[speciesCode] ?: 0.0) + area
                        val age = props.spec_age
                        if (age != null && age > 0) {
                            speciesAgeSumMap[speciesCode] = (speciesAgeSumMap[speciesCode] ?: 0.0) + age * area
                            speciesAgeCountMap[speciesCode] = (speciesAgeCountMap[speciesCode] ?: 0.0) + area
                        }
                    }
                }

                if (forestFunction == null && props.forest_fun != null) {
                    forestFunction = RdlpMapper.forestFunCodeToValue(props.forest_fun)
                }
                if (standStructure == null && props.stand_stru != null) {
                    standStructure = RdlpMapper.standStruCodeToValue(props.stand_stru)
                }
                if (siteType == null && props.site_type != null) {
                    siteType = RdlpMapper.siteTypeCodeToValue(props.site_type)
                }
                if (protectionCategory == null && props.prot_categ != null) {
                    protectionCategory = RdlpMapper.protCategCodeToValue(props.prot_categ)
                }
                if (rotationAge == null && props.rotat_age != null && props.rotat_age > 0) {
                    rotationAge = props.rotat_age
                }
            }

            val speciesBreakdown = speciesAreaMap.entries
                .sortedByDescending { it.value }
                .map { (code, area) ->
                    val percentage = if (totalArea > 0) (area / totalArea) * 100.0 else 0.0
                    val avgAge = if ((speciesAgeCountMap[code] ?: 0.0) > 0) {
                        (speciesAgeSumMap[code] ?: 0.0) / speciesAgeCountMap[code]!!
                    } else null
                    val ageLabel = avgAge?.let { classifyAge(it, RdlpMapper.speciesCodeToName(code)) }
                    SpeciesEntry(
                        speciesCode = code,
                        speciesName = RdlpMapper.speciesCodeToName(code),
                        percentage = Math.round(percentage * 10.0) / 10.0,
                        ageLabel = ageLabel
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

    private fun classifyAge(ageYears: Double, speciesName: String): String {
        val feminine = speciesName.endsWith("a")
        return when {
            ageYears < 40 -> if (feminine) "młoda" else "młody"
            ageYears <= 80 -> if (feminine) "średnia" else "średni"
            else -> if (feminine) "stara" else "stary"
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
