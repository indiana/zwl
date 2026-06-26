package com.indiana.zwl.presentation

import com.indiana.zwl.presentation.theme.*

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InZoneContent(
    forestDistrict: String,
    fireRiskLevel: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(GreenPrimary.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                    .border(3.dp, ForestGreenAccent, RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("ZwL", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ForestGreenAccent)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Jesteś w strefie\nZanocuj w Lesie",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = forestDistrict,
                fontSize = 18.sp,
                color = ForestGreenText,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Zagrożenie pożarowe w lasach",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                
                val riskText = when (fireRiskLevel) {
                    0 -> "STOPNIEŃ 0 (Brak zagrożenia)"
                    1 -> "STOPNIEŃ 1 (Niskie zagrożenie)"
                    2 -> "STOPNIEŃ 2 (Średnie zagrożenie)"
                    3 -> "STOPNIEŃ 3 (BARDZO WYSOKIE)"
                    10 -> "STOPNIEŃ 0 (Brak - archiwalne offline)"
                    11 -> "STOPNIEŃ 1 (Niskie - archiwalne offline)"
                    12 -> "STOPNIEŃ 2 (Średnie - archiwalne offline)"
                    13 -> "STOPNIEŃ 3 (WYSOKIE - archiwalne offline)"
                    -2 -> "Status pożarowy: Nieznany (brak sieci)"
                    else -> "Status pożarowy: Brak danych"
                }
                
                val riskColor = when (fireRiskLevel) {
                    0, 10 -> RiskLevelNone
                    1, 11 -> RiskLevelLow
                    2, 12 -> RiskLevelMedium
                    3, 13 -> RiskLevelHigh
                    else -> RiskLevelUnknown
                }

                Text(
                    text = riskText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = riskColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Używanie kuchenek gazowych",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (fireRiskLevel) {
                    0, 1, 2 -> {
                        Surface(
                            color = GreenPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GreenPrimary)
                        ) {
                            Text(
                                text = "DOZWOLONE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenAccent,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                    10, 11, 12 -> {
                        Surface(
                            color = GreenPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GreenPrimary)
                        ) {
                            Text(
                                text = "DOZWOLONE (dane archiwalne)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenAccent,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                    3 -> {
                        val infiniteTransition = rememberInfiniteTransition(label = " Stove Warning Pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )

                        Surface(
                            color = ErrorRedButton.copy(alpha = 0.2f * pulseAlpha),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, ErrorRedButton.copy(alpha = pulseAlpha)),
                            modifier = Modifier.alpha(pulseAlpha)
                        ) {
                            Text(
                                text = "BEZWZGLĘDNY ZAKAZ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRedAccent,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                    13 -> {
                        val infiniteTransition = rememberInfiniteTransition(label = " Stove Warning Pulse Offline")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )

                        Surface(
                            color = ErrorRedButton.copy(alpha = 0.2f * pulseAlpha),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, ErrorRedButton.copy(alpha = pulseAlpha)),
                            modifier = Modifier.alpha(pulseAlpha)
                        ) {
                            Text(
                                text = "BEZWZGLĘDNY ZAKAZ (dane archiwalne)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRedAccent,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = YellowSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, YellowSecondary)
                        ) {
                            Text(
                                text = "WARUNKOWO DOZWOLONE\n(brak aktualnych danych pożarowych)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiskLevelLow,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
