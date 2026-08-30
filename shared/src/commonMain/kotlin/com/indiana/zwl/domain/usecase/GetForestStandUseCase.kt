package com.indiana.zwl.domain.usecase

import com.indiana.zwl.domain.model.ForestStandSummary
import com.indiana.zwl.domain.model.SpeciesEntry
import com.indiana.zwl.domain.model.TranslatedCode
import com.indiana.zwl.domain.model.Zone
import com.indiana.zwl.domain.util.RdlpMapper
import com.indiana.zwl.shared.data.remote.BdlOgcApi
import kotlinx.coroutines.CancellationException
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import kotlin.math.round

/**
 * Fetches and aggregates the BDL forest-stand (drzewostan) summary for a zone.
 *
 * Lives in shared (KMP) so Android and iOS run the same logic for the
 * forest-stand card in zone details. JTS types come from the multiplatform
 * kts-core port and compile on every target.
 */
class GetForestStandUseCase(
    private val ogcApi: BdlOgcApi
) {
    suspend operator fun invoke(zone: Zone): Result<ForestStandSummary> {
        return try {
            val geometry: Geometry = WKTReader().read(zone.geometryWkt)
            val envelope: Envelope = geometry.getEnvelopeInternal()

            val bbox = "${envelope.getMinX()},${envelope.getMinY()},${envelope.getMaxX()},${envelope.getMaxY()}"
            val centre = envelope.centre()
                ?: return Result.failure(Exception("Nie udało się ustalić położenia strefy."))

            val regionResult = ogcApi.findNadlesnictwo(
                bbox = "${centre.x - 0.01},${centre.y - 0.01},${centre.x + 0.01},${centre.y + 0.01}"
            )
            val regionCd = regionResult.features.firstOrNull()?.properties?.get("region_cd")?.toString()?.trim('"')

            if (regionCd == null) {
                return Result.failure(Exception("Nie udało się ustalić regionu RDLP."))
            }

            val collectionId = RdlpMapper.collectionForRegionCode(regionCd)
                ?: return Result.failure(Exception("Nieznany region RDLP: $regionCd"))

            val stands = ogcApi.getForestStands(collectionId = collectionId, bbox = bbox)
            val features = stands.features

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

                val forestFun = props.forest_fun
                val standStru = props.stand_stru
                val siteTypeVal = props.site_type
                val protCateg = props.prot_categ
                val rotatAge = props.rotat_age

                if (forestFunction == null && forestFun != null) {
                    forestFunction = RdlpMapper.forestFunCodeToValue(forestFun)
                }
                if (standStructure == null && standStru != null) {
                    standStructure = RdlpMapper.standStruCodeToValue(standStru)
                }
                if (siteType == null && siteTypeVal != null) {
                    siteType = RdlpMapper.siteTypeCodeToValue(siteTypeVal)
                }
                if (protectionCategory == null && protCateg != null) {
                    protectionCategory = RdlpMapper.protCategCodeToValue(protCateg)
                }
                if (rotationAge == null && rotatAge != null && rotatAge > 0) {
                    rotationAge = rotatAge
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
                        percentage = round(percentage * 10.0) / 10.0,
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
                    totalAreaHa = round(totalArea * 100.0) / 100.0,
                    rotationAge = rotationAge
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println(e.stackTraceToString())
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