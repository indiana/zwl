package com.indiana.zwl.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.model.ForestBan
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.presentation.PendingPoint

@Composable
fun PointDetailCard(
    point: PendingPoint,
    onSave: (String) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(point.name ?: "") }

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
                Text(
                    text = "Wybrany punkt",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose) {
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "WSPÓŁRZĘDNE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.6f, %.6f", point.lat, point.lng),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            point.status?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "STREFA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText(status),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            point.ban?.let { ban ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ZAKAZ WSTĘPU",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = banReason(ban),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa punktu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onSave(name) },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zapisz", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Podziel się", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun statusText(status: LocationStatus): String {
    return when (status) {
        is LocationStatus.InZone -> "W strefie: ${status.forestDistrict}"
        is LocationStatus.OutsideZone ->
            "Poza strefą (najbliżej: ${status.nearestDistrict}, ok. ${formatDistance(status.distanceMeters)})"
        LocationStatus.EmptyData -> "Brak danych o strefach"
    }
}

private fun banReason(ban: ForestBan): String {
    return ban.description?.takeIf { it.isNotBlank() } ?: ban.forestDistrictName ?: "Zakaz wstępu"
}

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        val km = meters / 1000.0
        String.format(java.util.Locale.US, "%.1f km", km)
    }
}
