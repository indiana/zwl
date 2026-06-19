package com.indiana.zwl.presentation

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
    fireRiskLevel: Int,
    onSwitchToMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF2E7D32).copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                    .border(3.dp, Color(0xFF81C784), RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("ZwL", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
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
                color = Color(0xFFA5D6A7),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

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
                    else -> "Status pożarowy: Nieznany (brak sieci)"
                }
                
                val riskColor = when (fireRiskLevel) {
                    0 -> Color(0xFF81C784)
                    1 -> Color(0xFFFFF176)
                    2 -> Color(0xFFFFB74D)
                    3 -> Color(0xFFE57373)
                    else -> Color(0xFFB0BEC5)
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
                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Text(
                                text = "DOZWOLONE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784),
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
                            color = Color(0xFFC62828).copy(alpha = 0.2f * pulseAlpha),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, Color(0xFFC62828).copy(alpha = pulseAlpha)),
                            modifier = Modifier.alpha(pulseAlpha)
                        ) {
                            Text(
                                text = "BEZWZGLĘDNY ZAKAZ",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                    else -> {
                        Surface(
                            color = Color(0xFFF57F17).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFF57F17))
                        ) {
                            Text(
                                text = "WARUNKOWO DOZWOLONE\n(brak aktualnych danych pożarowych)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFF176),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSwitchToMap,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(bottom = 24.dp)
                .height(48.dp)
                .fillMaxWidth(0.6f)
        ) {
            Text("Pokaż na mapie", fontWeight = FontWeight.Bold)
        }
    }
}
