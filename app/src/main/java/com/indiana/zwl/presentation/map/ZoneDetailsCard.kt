package com.indiana.zwl.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.presentation.SelectedZoneDetails

@Composable
fun ZoneDetailsCard(
    details: SelectedZoneDetails,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = details.zone.forestDistrict,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Strefa Zanocuj w Lesie",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
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
                            text = "ODLEGŁOŚĆ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = details.distanceMeters?.let { formatDistance(it) } ?: "Obliczanie...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
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
                            text = "ZAGROŻENIE POŻAROWE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (details.isLoadingFireRisk) {
                            Box(modifier = Modifier.size(16.dp)) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val riskText = when (details.fireRiskLevel) {
                                0 -> "STOPIEŃ 0 (Brak)"
                                1 -> "STOPIEŃ 1 (Niskie)"
                                2 -> "STOPIEŃ 2 (Średnie)"
                                3 -> "STOPIEŃ 3 (WYSOKIE)"
                                10 -> "STOPIEŃ 0 (Brak - offline)"
                                11 -> "STOPIEŃ 1 (Niskie - offline)"
                                12 -> "STOPIEŃ 2 (Średnie - offline)"
                                13 -> "STOPIEŃ 3 (WYSOKIE - offline)"
                                -2 -> "Nieznany (brak sieci)"
                                else -> "Brak danych"
                            }
                            val riskColor = when (details.fireRiskLevel) {
                                0, 10 -> Color(0xFF81C784)
                                1, 11 -> Color(0xFFFFF176)
                                2, 12 -> Color(0xFFFFB74D)
                                3, 13 -> Color(0xFFE57373)
                                else -> Color(0xFFB0BEC5)
                            }
                            Text(
                                text = riskText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = riskColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "UŻYWANIE KUCHENEK GAZOWYCH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (details.isLoadingFireRisk) {
                        Text(
                            text = "Pobieranie zasad...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val permissionText = when (details.fireRiskLevel) {
                            0, 1, 2 -> "DOZWOLONE"
                            10, 11, 12 -> "DOZWOLONE (archiwalne)"
                            3 -> "BEZWZGLĘDNY ZAKAZ"
                            13 -> "BEZWZGLĘDNY ZAKAZ (archiwalne)"
                            -2 -> "WARUNKOWO DOZWOLONE (brak sieci)"
                            else -> "WARUNKOWO DOZWOLONE (brak danych)"
                        }
                        val permissionColor = when (details.fireRiskLevel) {
                            0, 1, 2 -> Color(0xFF81C784)
                            10, 11, 12 -> Color(0xFF81C784)
                            3 -> Color(0xFFEF5350)
                            13 -> Color(0xFFEF5350)
                            else -> Color(0xFFFFF176)
                        }
                        Text(
                            text = permissionText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = permissionColor
                        )
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        val km = meters / 1000.0
        String.format(java.util.Locale.US, "%.1f km", km)
    }
}
