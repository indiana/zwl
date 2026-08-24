package com.indiana.zwl.presentation

import com.indiana.zwl.presentation.theme.*

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.indiana.zwl.domain.model.ForestBan
import androidx.compose.material.icons.filled.Warning

@Composable
fun InZoneContent(
    forestDistrict: String,
    fireRiskLevel: Int,
    currentForestBan: ForestBan? = null,
    onViewDetailsClick: (() -> Unit)? = null,
    onBanDetailsClick: (() -> Unit)? = null,
    onDebugToggle: (() -> Unit)? = null,
    isActive: Boolean = true
) {
    // Unbounded-constraint assumption: the host (Scaffold content Box with fillMaxSize) always
    // provides finite maxHeight, including the hidden tab (size(0.dp) -> maxHeight = 0.dp).
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        if (currentForestBan != null && onBanDetailsClick != null) {
            ForestBanAlertBanner(
                forestBan = currentForestBan,
                onBanDetailsClick = onBanDetailsClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(GreenPrimary.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                    .border(3.dp, ForestGreenAccent, RoundedCornerShape(50.dp))
                    .clickable(enabled = onDebugToggle != null) {
                        onDebugToggle?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ForestGreenAccent,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Jesteś w strefie\nprogramu \"Zanocuj w Lesie\"",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                onClick = { onViewDetailsClick?.invoke() },
                color = GreenPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ForestGreenAccent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = forestDistrict,
                        fontSize = 18.sp,
                        color = ForestGreenText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Szczegóły strefy",
                        tint = ForestGreenAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
                
                val riskText = fireRiskStatusText(fireRiskLevel)
                
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
                        PulsingBanBadge(
                            text = "BEZWZGLĘDNY ZAKAZ",
                            isActive = isActive
                        )
                    }
                    13 -> {
                        PulsingBanBadge(
                            text = "BEZWZGLĘDNY ZAKAZ (dane archiwalne)",
                            fontSize = 16.sp,
                            isActive = isActive
                        )
                    }
                    else -> {
                        Surface(
                            color = ErrorRedButton.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ErrorRedButton)
                        ) {
                            Text(
                                text = "BRAK DANYCH\nnie używaj kuchenek gazowych\nsprawdź komunikat w nadleśnictwie",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ErrorRedAccent,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = GAS_STOVE_STATUS_DISCLAIMER,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }
    }
    }
}

internal const val GAS_STOVE_STATUS_DISCLAIMER =
    "Status kuchenek wyznaczany jest wyłącznie na podstawie stopnia zagrożenia pożarowego. Aby mieć absolutną pewność, sprawdź stronę swojego nadleśnictwa."

internal fun fireRiskStatusText(level: Int): String = when (level) {
    0 -> "STOPNIEŃ 0 (Brak zagrożenia)"
    1 -> "STOPNIEŃ 1 (Niskie zagrożenie)"
    2 -> "STOPNIEŃ 2 (Średnie zagrożenie)"
    3 -> "STOPNIEŃ 3 (BARDZO WYSOKIE)"
    10 -> "STOPNIEŃ 0 (Brak - archiwalne offline)"
    11 -> "STOPNIEŃ 1 (Niskie - archiwalne offline)"
    12 -> "STOPNIEŃ 2 (Średnie - archiwalne offline)"
    13 -> "STOPNIEŃ 3 (WYSOKIE - archiwalne offline)"
    else -> "Status pożarowy: Brak danych"   // UNIFIED: covers -2 and any unknown level
}

internal fun shouldPulse(isActive: Boolean, animatorDurationScale: Float): Boolean =
    isActive && animatorDurationScale > 0f

@Composable
internal fun PulsingBanBadge(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val animatorScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    val pulseAlpha = if (shouldPulse(isActive, animatorScale)) {
        val infiniteTransition = rememberInfiniteTransition(label = "BanBadgePulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseAlpha"
        )
        alpha
    } else {
        1f
    }

    Surface(
        modifier = modifier,
        color = ErrorRedButton.copy(alpha = 0.2f * pulseAlpha),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(2.dp, ErrorRedButton.copy(alpha = pulseAlpha))
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = ErrorRedAccent,                       // ALWAYS full alpha — never fades
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun ForestBanAlertBanner(
    forestBan: ForestBan,
    onBanDetailsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onBanDetailsClick,
        color = ErrorDarkBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, ErrorRedAccent),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(ErrorRedButton.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRedAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ZAKAZ WSTĘPU DO LASU",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRedAccent
                )
                Text(
                    text = "${banReasonText(forestBan.reason)} (${forestBan.forestDistrictName})",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onBanDetailsClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Szczegóły zakazu",
                    tint = ErrorRedAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun banReasonText(reason: String): String {
    return reason.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}

