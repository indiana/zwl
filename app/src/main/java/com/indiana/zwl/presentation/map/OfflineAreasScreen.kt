package com.indiana.zwl.presentation.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.model.DownloadedArea
import com.indiana.zwl.shared.offline.OfflineAreaNames
import java.util.Locale
import java.util.TimeZone

/**
 * Full-screen overlay listing downloaded offline areas (iOS parity:
 * `OfflineAreasView`). Tap on an area flies the map camera to its bbox;
 * rows offer rename / refresh / delete, the toolbar offers delete-all.
 */
@Composable
fun OfflineAreasScreen(
    areas: List<DownloadedArea>,
    isOffline: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onAreaTap: (DownloadedArea) -> Unit,
    onDeleteArea: (DownloadedArea) -> Unit,
    onDeleteAll: () -> Unit,
    onRenameArea: (Long, String) -> Unit,
    onRefreshArea: (DownloadedArea) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val offsetMinutes = remember { TimeZone.getDefault().getOffset(now) / 60_000 }

    var areaToDelete by remember { mutableStateOf<DownloadedArea?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var areaToRename by remember { mutableStateOf<DownloadedArea?>(null) }

    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pobrane obszary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Zamknij",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (areas.isNotEmpty()) {
                    Text(
                        text = "${areas.size} ${pluralArea(areas.size)} · ${formatBytes(areas.sumOf { it.fileSizeBytes })}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (areas.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Brak pobranych obszarów",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Otwórz menu mapy i wybierz „Pobierz obszar” w miejscu, które chcesz mieć offline.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(areas, key = { it.id }) { area ->
                            OfflineAreaRow(
                                area = area,
                                ageLabel = OfflineAreaNames.ageLabel(area.downloadedAt, now, offsetMinutes),
                                actionsEnabled = !isDownloading,
                                isOffline = isOffline,
                                onTap = { onAreaTap(area) },
                                onRename = { areaToRename = area },
                                onRefresh = { onRefreshArea(area) },
                                onDelete = { areaToDelete = area }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { confirmDeleteAll = true },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Usuń wszystkie obszary", fontSize = 12.sp)
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Trwa pobieranie — zarządzanie zablokowane do końca pobierania.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    areaToDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            title = { Text("Usunąć obszar?") },
            text = { Text("„${area.name}” zostanie usunięty z urządzenia. Mapy offline w tym miejscu przestaną być dostępne.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteArea(area)
                    areaToDelete = null
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { areaToDelete = null }) { Text("Anuluj") }
            }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Usunąć wszystkie obszary?") },
            text = { Text("Wszystkie pobrane mapy offline (${formatBytes(areas.sumOf { it.fileSizeBytes })}) zostaną usunięte z urządzenia.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAll()
                    confirmDeleteAll = false
                }) { Text("Usuń wszystkie", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Anuluj") }
            }
        )
    }

    areaToRename?.let { area ->
        var nameInput by remember(area.id) { mutableStateOf(area.name) }
        AlertDialog(
            onDismissRequest = { areaToRename = null },
            title = { Text("Zmień nazwę obszaru") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Nazwa") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameArea(area.id, trimmed)
                        }
                        areaToRename = null
                    },
                    enabled = nameInput.isNotBlank()
                ) { Text("Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { areaToRename = null }) { Text("Anuluj") }
            }
        )
    }
}

@Composable
private fun OfflineAreaRow(
    area: DownloadedArea,
    ageLabel: String,
    actionsEnabled: Boolean,
    isOffline: Boolean,
    onTap: () -> Unit,
    onRename: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = area.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$ageLabel · zoom ${area.minZoom}–${area.maxZoom}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${area.tileCount} kafelków · ${formatBytes(area.fileSizeBytes)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    IconButton(onClick = onRefresh, enabled = actionsEnabled && !isOffline) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Odśwież",
                            tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onRename, enabled = actionsEnabled) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Zmień nazwę",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, enabled = actionsEnabled) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Usuń",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

private fun pluralArea(count: Int): String = when {
    count == 1 -> "obszar"
    count in 2..4 -> "obszary"
    else -> "obszarów"
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format(Locale.getDefault(), "%.0f kB", kb)
        kb < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", kb / 1024)
        else -> String.format(Locale.getDefault(), "%.2f GB", kb / 1024 / 1024)
    }
}
