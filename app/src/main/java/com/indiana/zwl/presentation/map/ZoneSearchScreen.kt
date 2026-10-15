package com.indiana.zwl.presentation.map

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.usecase.ZoneMatchSpan
import com.indiana.zwl.domain.usecase.ZoneSearchResult
import com.indiana.zwl.domain.usecase.ZoneSortMode
import java.util.Locale

/**
 * Full-screen overlay searching zones (nadleśnictwa) by name — iOS parity:
 * `ZoneSearchView`). A tap only flies the map camera to the zone's bounding
 * box; the sort toggle switches between alphabetical and distance ordering.
 */
@Composable
fun ZoneSearchScreen(
    results: List<ZoneSearchResult>,
    query: String,
    sortMode: ZoneSortMode,
    hasLocation: Boolean,
    onQueryChange: (String) -> Unit,
    onSortModeChange: (ZoneSortMode) -> Unit,
    onDismiss: () -> Unit,
    onZoneTap: (ZoneSearchResult) -> Unit
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (isLandscape) 4.dp else 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(if (isLandscape) 36.dp else 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz",
                            modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Szukaj strefy",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isLandscape) 15.sp else 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(
                    color = androidx.compose.ui.graphics.Color.DarkGray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nazwa nadleśnictwa…", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Wyczyść"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortMode == ZoneSortMode.ALPHABETICAL,
                            onClick = { onSortModeChange(ZoneSortMode.ALPHABETICAL) },
                            label = { Text("A–Z", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = sortMode == ZoneSortMode.DISTANCE,
                            onClick = { onSortModeChange(ZoneSortMode.DISTANCE) },
                            enabled = hasLocation,
                            label = { Text("Odległość", fontSize = 12.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (results.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            emptyState(query)
                        }
                    } else {
                        Text(
                            text = "${results.size} ${pluralStref(results.size)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(results, key = { it.zoneInfo.id }) { result ->
                                ZoneSearchRow(result = result, onTap = { onZoneTap(result) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneSearchRow(
    result: ZoneSearchResult,
    onTap: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightedName(result.zoneInfo.forestDistrict, result.matchSpans),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                result.distanceMeters?.let { distance ->
                    Text(
                        text = "Odległość: ${formatDistance(distance)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightedName(name: String, spans: List<ZoneMatchSpan>): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        val sorted = spans
            .filter { it.start >= 0 && it.end > it.start && it.end <= name.length }
            .sortedBy { it.start }
        for (span in sorted) {
            if (span.start < cursor) continue
            append(name.substring(cursor, span.start))
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(name.substring(span.start, span.end))
            }
            cursor = span.end
        }
        append(name.substring(cursor))
    }

@Composable
private fun emptyState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (query.isBlank()) "Brak stref" else "Brak wyników",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        if (query.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Żadna strefa nie zawiera „$query” w nazwie.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Baza stref jest pusta — uruchom synchronizację danych.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun pluralStref(count: Int): String = when {
    count == 1 -> "strefa"
    count in 2..4 -> "strefy"
    else -> "stref"
}

private fun formatDistance(meters: Double): String {
    return if (meters < 100.0) {
        "${meters.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000.0)
    }
}
