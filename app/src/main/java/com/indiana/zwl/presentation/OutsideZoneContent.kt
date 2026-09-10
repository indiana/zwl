package com.indiana.zwl.presentation

import android.content.res.Configuration
import com.indiana.zwl.presentation.theme.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.indiana.zwl.domain.model.ForestBan

@Composable
fun OutsideZoneContent(
    nearestDistrict: String,
    distanceMeters: Double,
    bearingDegrees: Float,
    azimuth: Float,
    currentForestBan: ForestBan? = null,
    onViewDetailsClick: (() -> Unit)? = null,
    onBanDetailsClick: (() -> Unit)? = null,
    onDebugToggle: (() -> Unit)? = null
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var accumulatedAzimuth by remember { mutableStateOf(azimuth) }
    LaunchedEffect(azimuth) {
        val diff = (azimuth - accumulatedAzimuth) % 360f
        val shortestDiff = ((diff + 540f) % 360f) - 180f
        accumulatedAzimuth += shortestDiff
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = accumulatedAzimuth,
        label = "AnimatedAzimuth"
    )

    val animatedBearing by animateFloatAsState(
        targetValue = bearingDegrees,
        label = "AnimatedBearing"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val portraitCompassSize = minOf(220.dp, maxHeight / 2f)
        val landscapeCompassSize = maxOf(140.dp, minOf(200.dp, maxHeight * 0.6f))
        val compassSize = if (isLandscape) landscapeCompassSize else portraitCompassSize
        val compassRadius = compassSize / 2
        val needleSize = minOf(80.dp, compassSize * 0.36f)
        val innerPadding = minOf(12.dp, compassSize * 0.06f)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(if (isLandscape) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.SpaceBetween
        ) {
        if (currentForestBan != null && onBanDetailsClick != null) {
            ForestBanAlertBanner(
                forestBan = currentForestBan,
                onBanDetailsClick = onBanDetailsClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        val heroContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = if (isLandscape) 0.dp else 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 72.dp else 100.dp)
                    .background(AmberAccent.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                    .border(3.dp, YellowPrimary, RoundedCornerShape(50.dp))
                    .clickable(enabled = onDebugToggle != null) {
                        onDebugToggle?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "!",
                    fontSize = if (isLandscape) 36.sp else 48.sp,
                    fontWeight = FontWeight.Black,
                    color = YellowPrimary
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

            Text(
                text = "Jesteś poza strefą\nprogramu \"Zanocuj w Lesie\"",
                fontSize = if (isLandscape) 20.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = if (isLandscape) 24.sp else 32.sp
            )
        }
        }

        val compassContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = if (isLandscape) 0.dp else 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(compassSize)
                    .rotate(-animatedAzimuth)
                    .border(2.dp, Color.DarkGray, RoundedCornerShape(compassRadius)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text("N", modifier = Modifier.align(Alignment.TopCenter), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("S", modifier = Modifier.align(Alignment.BottomCenter), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("W", modifier = Modifier.align(Alignment.CenterStart), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("E", modifier = Modifier.align(Alignment.CenterEnd), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Canvas(
                    modifier = Modifier
                        .size(needleSize)
                        .rotate(animatedBearing)
                ) {
                    val path = Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width / 2, size.height * 0.75f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, color = YellowPrimary)
                }
            }
        }
        }

        val nearestCardContent: @Composable () -> Unit = {
        Card(
            onClick = { onViewDetailsClick?.invoke() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLandscape) 0.dp else 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (isLandscape) 14.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NAJBLIŻSZA STREFA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = nearestDistrict,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Szczegóły strefy",
                        tint = YellowPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatDistance(distanceMeters),
                    fontSize = 16.sp,
                    color = RiskLevelLow,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Kierunek: ${getCardinalDirection(bearingDegrees)}",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        }
        }
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    heroContent()
                    Spacer(modifier = Modifier.height(16.dp))
                    nearestCardContent()
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { compassContent() }
            }
        } else {
            heroContent()
            compassContent()
            nearestCardContent()
        }
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
