package com.indiana.zwl.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.model.NearbyPoint
import com.indiana.zwl.domain.model.NearbyPointType
import java.util.Locale

/**
 * "Pobliskie punkty" section for the point detail cards: points of every type
 * within the configured radius, sorted by distance, capped by the use case.
 * The empty state is a single line — no header, no container — so it stays
 * unobtrusive when nothing is nearby. Radius 0 turns the feature off and the
 * section is not rendered at all. Tapping a row opens that point's detail.
 */
@Composable
fun NearbyPointsSection(
    points: List<NearbyPoint>,
    totalCount: Int,
    radiusKm: Float,
    onPointClick: (NearbyPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    // Radius 0 = feature off: no section anywhere.
    if (radiusKm <= 0f) return

    if (points.isEmpty()) {
        Text(
            text = "Brak innych punktów w promieniu ${formatRadiusKm(radiusKm)}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "POBLISKIE PUNKTY ($totalCount)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
        ) {
            points.forEach { point ->
                NearbyPointRow(point = point, onClick = { onPointClick(point) })
            }
            val hidden = totalCount - points.size
            if (hidden > 0) {
                Text(
                    text = "…i $hidden więcej w promieniu",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NearbyPointRow(
    point: NearbyPoint,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(point.type.dotColor(), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
            Text(
                text = point.name,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = point.subtitle.ifBlank { "Mój punkt" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatNearbyDistance(point.distanceMeters),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun NearbyPointType.dotColor(): Color = when (this) {
    NearbyPointType.OWN_POINT -> Color(0xFFE91E63)
    NearbyPointType.POI -> Color(0xFF1976D2)
    NearbyPointType.WATER_SOURCE -> Color(0xFF1565C0)
}

/** Same format the detail cards use: whole meters below 100 m, one-decimal km above. */
internal fun formatNearbyDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    }
}

internal fun formatRadiusKm(radiusKm: Float): String =
    String.format(Locale.US, "%.1f km", radiusKm)
