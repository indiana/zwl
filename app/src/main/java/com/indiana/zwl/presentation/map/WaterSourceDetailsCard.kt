package com.indiana.zwl.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.model.DrinkingWaterStatus
import com.indiana.zwl.domain.model.WaterSourceType
import com.indiana.zwl.presentation.SelectedWaterSourceDetails
import com.indiana.zwl.shared.data.water.WaterAttribution

@Composable
fun WaterSourceDetailsCard(
    details: SelectedWaterSourceDetails,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = details.waterSource

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxContentHeight = (maxHeight - 32.dp).let { if (it < 0.dp) 0.dp else it }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.name.ifBlank { "Źródło wody" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = waterSourceTypeLabel(source.type),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose) {
                        Text(
                            text = "✕",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                InfoBlock(
                    title = "STATUS WODY",
                    value = drinkingWaterLabel(source.drinkingWater)
                )

                source.depthMeters?.let { depth ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(
                        title = "GŁĘBOKOŚĆ",
                        value = "${depth.toInt()} m"
                    )
                }

                source.pump?.let { pump ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "POMPA", value = pumpLabel(pump))
                }

                source.fountain?.let { fountain ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "RODZAJ PUNKTU", value = fountainLabel(fountain))
                }

                source.bottle?.let { bottle ->
                    bottleLabel(bottle)?.let { label ->
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoBlock(title = "NAPEŁNIANIE BUTELKI", value = label)
                    }
                }

                source.drinkingWaterRaw?.let { raw ->
                    drinkingWaterRawLabel(raw)?.let { label ->
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoBlock(title = "SZCZEGÓŁY WODY", value = label)
                    }
                }

                source.seasonal?.let { seasonal ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "SEZONOWOŚĆ", value = seasonalLabel(seasonal))
                }

                source.intermittent?.let { intermittent ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "DOSTĘPNOŚĆ", value = intermittentLabel(intermittent))
                }

                source.fee?.let { fee ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "OPŁATA", value = feeLabel(fee))
                }

                source.openingHours?.let { openingHours ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "GODZINY OTWARCIA", value = openingHours)
                }

                source.operator?.let { operator ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "OPERATOR", value = operator)
                }

                source.description?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoBlock(title = "OPIS", value = description)
                }

                Spacer(modifier = Modifier.height(8.dp))

                InfoBlock(
                    title = "ODLEGŁOŚĆ OD TWOJEJ POZYCJI",
                    value = details.distanceMeters?.let { formatDistance(it) } ?: "Obliczanie..."
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ŹRÓDŁO DANYCH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = WaterAttribution.OSM,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = WaterAttribution.WATER_OSM_DISCLAIMER,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (source.drinkingWater != DrinkingWaterStatus.YES) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = WaterAttribution.WATER_POTENTIAL_DISCLAIMER,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

internal fun waterSourceTypeLabel(type: WaterSourceType): String = when (type) {
    WaterSourceType.DRINKING_WATER -> "Woda pitna"
    WaterSourceType.WATER_TAP -> "Kran z wodą"
    WaterSourceType.WATER_POINT -> "Punkt poboru wody"
    WaterSourceType.SPRING -> "Źródło"
    WaterSourceType.WELL -> "Studnia"
    WaterSourceType.FOUNTAIN -> "Fontanna"
    WaterSourceType.WATER_ON_SITE -> "Woda na miejscu (biwak/wiata)"
    WaterSourceType.REFILL -> "Punkt napełniania butelek"
}

internal fun drinkingWaterLabel(status: DrinkingWaterStatus): String = when (status) {
    DrinkingWaterStatus.YES -> "Pitna (potwierdzona)"
    DrinkingWaterStatus.NO -> "Niepitna"
    DrinkingWaterStatus.UNKNOWN -> "Nieznany"
}

internal fun pumpLabel(raw: String): String {
    return when (firstToken(raw)) {
        "manual", "hand_pump" -> "Pompa ręczna"
        "no" -> "Otwarty szyb (własna lina)"
        "powered" -> "Pompa mechaniczna"
        "automatic" -> "Pompa automatyczna"
        "yes" -> "Pompa (rodzaj nieznany)"
        else -> raw
    }
}

internal fun drinkingWaterRawLabel(raw: String): String? = when (raw.lowercase()) {
    "boil" -> "Woda pitna po przegotowaniu"
    "treated" -> "Woda uzdatniona"
    "untreated" -> "Woda nieuzdatniona"
    "mineral" -> "Woda mineralna"
    "seasonal" -> "Dostępna sezonowo"
    "conditional" -> "Dostępna warunkowo"
    "manantial" -> "Źródło"
    "bubbler" -> "Poidełko"
    "fountain" -> "Fontanna"
    "yes", "true", "1", "no", "false", "0", "unknown", "fixme" -> null
    else -> raw
}

internal fun seasonalLabel(raw: String): String = translateTokens(raw) { token ->
    when (token) {
        "yes", "true", "1" -> "sezonowo"
        "no", "false", "0" -> "całorocznie"
        "spring" -> "wiosna"
        "summer" -> "lato"
        "autumn" -> "jesień"
        "winter" -> "zima"
        "wet_season" -> "pora deszczowa"
        "dry_season" -> "pora sucha"
        else -> token
    }
}

internal fun fountainLabel(raw: String): String = translateTokens(raw) { token ->
    when (token) {
        "bubbler" -> "poidełko"
        "drinking" -> "fontanna pitna"
        "bottle_refill" -> "napełnianie butelek"
        "water_tap", "tap" -> "kran"
        "nozzle" -> "dysza"
        "stone_block" -> "blok kamienny"
        "water_dispenser" -> "dozownik wody"
        "decorative" -> "dekoracyjna"
        "yes" -> "fontanna"
        else -> token
    }
}

internal fun bottleLabel(raw: String): String? = when (raw.lowercase()) {
    "yes" -> "Można napełnić butelkę"
    "designated" -> "Wyznaczone do napełniania"
    "limited" -> "Ograniczona możliwość"
    "no", "false", "0" -> null
    else -> raw
}

internal fun intermittentLabel(raw: String): String = when (raw.lowercase()) {
    "yes", "true", "1" -> "Okresowo (może nie działać)"
    "no", "false", "0" -> "Stale"
    else -> raw
}

internal fun feeLabel(raw: String): String = when (raw.lowercase()) {
    "yes", "true", "1" -> "Płatne"
    "no", "false", "0" -> "Bezpłatne"
    else -> raw
}

private fun firstToken(raw: String): String =
    raw.lowercase().split(';', ',', ' ').firstOrNull { it.isNotBlank() }?.trim().orEmpty()

private fun translateTokens(raw: String, translate: (String) -> String): String {
    val parts = raw.lowercase().split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return raw
    val joined = parts.joinToString(", ") { translate(it) }
    return joined.replaceFirstChar { it.uppercase() }
}

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        val km = meters / 1000.0
        String.format(java.util.Locale.US, "%.1f km", km)
    }
}
