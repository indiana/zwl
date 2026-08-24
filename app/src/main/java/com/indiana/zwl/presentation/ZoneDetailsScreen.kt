package com.indiana.zwl.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.util.BdlInfo
import com.indiana.zwl.presentation.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailsScreen(
    details: SelectedZoneDetails,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = details.zone.forestDistrict,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Strefa programu \"Zanocuj w Lesie\"",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkForestBackground
                )
            )
        },
        containerColor = DarkForestBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 0: Nadleśnictwo Website
            details.zone.websiteUrl?.let { websiteUrl ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "STRONA NADLEŚNICTWA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        WebsiteLinkRow(url = websiteUrl)
                    }
                }
            }

            // Card 1: Distance & Location Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ODLEGŁOŚĆ OD LOKALIZACJI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val distText = details.distanceMeters?.let { meters ->
                        if (meters == 0.0) "Jesteś na terenie tej strefy"
                        else formatDistance(meters)
                    } ?: "Obliczanie odległości..."

                    Text(
                        text = distText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenAccent
                    )
                }
            }

            // Card 2: Fire Risk & Stove Rules
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ZAGROŻENIE POŻAROWE I ZASADY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Fire Risk Level
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Stopień zagrożenia pożarowego:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (details.isLoadingFireRisk) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = ForestGreenAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pobieranie aktualnych danych...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                0, 10 -> RiskLevelNone
                                1, 11 -> RiskLevelLow
                                2, 12 -> RiskLevelMedium
                                3, 13 -> RiskLevelHigh
                                else -> RiskLevelUnknown
                            }
                            Surface(
                                color = riskColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, riskColor)
                            ) {
                                Text(
                                    text = riskText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = riskColor,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        thickness = 1.dp
                    )

                    // Gas Stove Rules
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Używanie kuchenek gazowych:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (details.isLoadingFireRisk) {
                            Text(
                                text = "Pobieranie aktualnych zasad...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            when (details.fireRiskLevel) {
                                0, 1, 2, 10, 11, 12 -> {
                                    Surface(
                                        color = GreenPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, GreenPrimary)
                                    ) {
                                        Text(
                                            text = if (details.fireRiskLevel in 10..12) "DOZWOLONE (dane archiwalne)" else "DOZWOLONE",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ForestGreenAccent,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                3, 13 -> {
                                    val infiniteTransition = rememberInfiniteTransition(label = "Stove Warning Pulse")
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
                                            text = if (details.fireRiskLevel == 13) "BEZWZGLĘDNY ZAKAZ (dane archiwalne)" else "BEZWZGLĘDNY ZAKAZ",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ErrorRedAccent,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                            text = "WARUNKOWO DOZWOLONE (brak danych pożarowych)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiskLevelLow,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = GAS_STOVE_STATUS_DISCLAIMER,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Card 3: Forest Stand Breakdown & Metadata
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "STRUKTURA I CHARAKTERYSTYKA DRZEWOSTANU",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (details.isLoadingForestStand) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = ForestGreenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Pobieranie szczegółowych danych z Banku Danych o Lasach...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (details.forestStand == null) {
                        Text(
                            text = "Brak szczegółowych danych o drzewostanie dla wybranego obszaru.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val summary = details.forestStand

                        if (summary.totalAreaHa > 0) {
                            Text(
                                text = "Powierzchnia drzewostanu: ${String.format(java.util.Locale.US, "%.1f", summary.totalAreaHa)} ha",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (summary.speciesBreakdown.isNotEmpty()) {
                            Text(
                                text = "Podział gatunkowy:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            summary.speciesBreakdown.forEach { entry ->
                                val displayName = if (entry.ageLabel != null) {
                                    "${entry.speciesName} (${entry.ageLabel})"
                                } else {
                                    entry.speciesName
                                }
                                val wikipediaTitle = BdlInfo.wikipediaTitleForSpecies(entry.speciesCode)
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (wikipediaTitle != null) {
                                                ForestGreenAccent
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            textDecoration = if (wikipediaTitle != null) TextDecoration.Underline else TextDecoration.None,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (wikipediaTitle != null) {
                                                        Modifier.clickable { openWikipedia(context, wikipediaTitle) }
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                        )
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.1f", entry.percentage)}%",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ForestGreenAccent
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { (entry.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp),
                                        color = ForestGreenAccent,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        val metadataItems = mutableListOf<MetadataItem>()
                        summary.forestFunction?.let {
                            metadataItems.add(MetadataItem("Funkcja lasu", it.name, BdlInfo.tooltipForForestFun(it.code)))
                        }
                        summary.standStructure?.let {
                            metadataItems.add(MetadataItem("Struktura drzewostanu", it.name, BdlInfo.tooltipForStandStru(it.code)))
                        }
                        summary.siteType?.let {
                            metadataItems.add(MetadataItem("Typ siedliskowy lasu", it.name, BdlInfo.tooltipForSiteType(it.code)))
                        }
                        summary.protectionCategory?.let {
                            metadataItems.add(MetadataItem("Kategoria ochrony", it.name, BdlInfo.tooltipForProtCateg(it.code)))
                        }
                        summary.rotationAge?.let {
                            metadataItems.add(MetadataItem("Wiek rębności", "${it} lat", BdlInfo.rotationAgeTooltip))
                        }

                        if (metadataItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                thickness = 1.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Parametry siedliska i gospodarki:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            metadataItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.label,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.tooltip == null) {
                                        Text(
                                            text = item.value,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        MetadataValueWithTooltip(value = item.value, tooltip = item.tooltip)
                                    }
                                }
                            }
                        }
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

private data class MetadataItem(
    val label: String,
    val value: String,
    val tooltip: String?
)

private fun openWikipedia(context: Context, articleTitle: String) {
    val uri = Uri.Builder()
        .scheme("https")
        .authority("pl.wikipedia.org")
        .appendPath("wiki")
        .appendPath(articleTitle)
        .build()
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
        .onFailure { /* no browser / no handler: silently ignore; the app stays usable */ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataValueWithTooltip(value: String, tooltip: String) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltip,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        state = tooltipState,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider()
    ) {
        Row(
            modifier = Modifier.clickable { scope.launch { tooltipState.show() } },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Pokaż wyjaśnienie",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
