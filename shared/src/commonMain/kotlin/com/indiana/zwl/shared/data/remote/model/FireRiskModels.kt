package com.indiana.zwl.shared.data.remote.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class FireRiskGeoJson(
    val features: List<FireRiskFeature>?
)

@Serializable
data class FireRiskFeature(
    val properties: FireRiskProperties
)

@Serializable
data class FireRiskProperties(
    @Serializable(with = FlexibleDoubleSerializer::class)
    val kod: Double? = null,
    val opis: String? = null
) {
    val kodInt: Int? get() = kod?.toInt()
}

/**
 * The BDL ArcGIS layer used to return `kod` as a number but now emits it as a
 * string (`"kod":"2"`), which broke deserialization and made every point read
 * as "brak danych". Accept numbers, numeric strings, and null/unparseable
 * values (→ null) so the app survives either server format.
 */
object FlexibleDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val element = json.decodeJsonElement()
        if (element !is JsonPrimitive) return null
        val content = element.content
        if (content.isBlank() || content == "null") return null
        return content.toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeDouble(value)
        }
    }
}
