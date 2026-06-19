package com.example.zwl.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OutsideZoneContent(
    nearestDistrict: String,
    distanceMeters: Double,
    bearingDegrees: Float,
    azimuth: Float,
    onSwitchToMap: () -> Unit
) {
    val needleRotation = (bearingDegrees - azimuth + 360f) % 360f

    val animatedRotation by animateFloatAsState(
        targetValue = needleRotation,
        label = "NeedleRotation"
    )

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
                    .background(Color(0xFFFFB300).copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                    .border(3.dp, Color(0xFFFBC02D), RoundedCornerShape(50.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("!", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFFFBC02D))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Jesteś poza strefą\nZanocuj w Lesie",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .border(2.dp, Color.DarkGray, RoundedCornerShape(110.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text("N", modifier = Modifier.align(Alignment.TopCenter), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("S", modifier = Modifier.align(Alignment.BottomCenter), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("W", modifier = Modifier.align(Alignment.CenterStart), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("E", modifier = Modifier.align(Alignment.CenterEnd), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Canvas(
                    modifier = Modifier
                        .size(80.dp)
                        .rotate(animatedRotation)
                ) {
                    val path = Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width / 2, size.height * 0.75f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, color = Color(0xFFFBC02D))
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NAJBLIŻSZA STREFA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = nearestDistrict,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatDistance(distanceMeters),
                    fontSize = 16.sp,
                    color = Color(0xFFFFF176),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Kierunek: ${getCardinalDirection(bearingDegrees)}",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
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

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "Odległość: ${meters.toInt()} m"
    } else {
        val km = meters / 1000.0
        String.format(java.util.Locale.US, "Odległość: %.1f km", km)
    }
}

private fun getCardinalDirection(bearing: Float): String {
    return when (bearing) {
        in 337.5..360.0 -> "Północny (N)"
        in 0.0..22.5 -> "Północny (N)"
        in 22.5..67.5 -> "Północny-Wschód (NE)"
        in 67.5..112.5 -> "Wschód (E)"
        in 112.5..157.5 -> "Południowy-Wschód (SE)"
        in 157.5..202.5 -> "Południowy (S)"
        in 202.5..247.5 -> "Południowy-Zachód (SW)"
        in 247.5..292.5 -> "Zachodni (W)"
        in 292.5..337.5 -> "Północny-Zachód (NW)"
        else -> "Nieznany"
    }
}
