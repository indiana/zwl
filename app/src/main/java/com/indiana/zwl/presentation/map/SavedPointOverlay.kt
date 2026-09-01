package com.indiana.zwl.presentation.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import com.indiana.zwl.domain.model.SavedPoint

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedPointListOverlay(
    points: List<SavedPoint>,
    onClose: () -> Unit,
    onSelect: (SavedPoint) -> Unit,
    onOpenProperties: (SavedPoint) -> Unit,
    onPasteCoordinates: (Double, Double) -> Unit
) {
    var pasteDialogVisible by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    var pasteError by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Zamknij"
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Zapisane punkty",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 1.dp)

            OutlinedButton(
                onClick = { pasteDialogVisible = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Otwórz punkt ze współrzędnych", fontWeight = FontWeight.SemiBold)
            }

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Brak zapisanych punktów.\nZaznacz punkt długim tapnięciem na mapie.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(points, key = { it.id }) { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onSelect(point) },
                                    onLongClick = { onOpenProperties(point) }
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = point.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%.5f, %.5f",
                                        point.latitude,
                                        point.longitude
                                    ),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = Color.DarkGray.copy(alpha = 0.2f),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }

    if (pasteDialogVisible) {
        AlertDialog(
            onDismissRequest = { pasteDialogVisible = false },
            title = { Text("Otwórz punkt ze współrzędnych") },
            text = {
                Column {
                    Text(
                        text = "Wklej współrzędne, np. 52.123456, 21.123456.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = {
                            pasteInput = it
                            pasteError = false
                        },
                        singleLine = true,
                        isError = pasteError,
                        label = { Text("Szerokość, Długość") }
                    )
                    if (pasteError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nie rozpoznano współrzędnych.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parseCoordinates(pasteInput)?.let { (lat, lng) ->
                            pasteDialogVisible = false
                            onPasteCoordinates(lat, lng)
                        } ?: run { pasteError = true }
                    }
                ) {
                    Text("Otwórz punkt")
                }
            },
            dismissButton = {
                TextButton(onClick = { pasteDialogVisible = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

private fun parseCoordinates(input: String): Pair<Double, Double>? {
    val pattern = Regex(
        """\s*([-]?\d+(?:[.,]\d+)?)\s*[,;\s]\s*([-]?\d+(?:[.,]\d+)?)\s*"""
    )
    val match = pattern.matchEntire(input.trim()) ?: return null
    val lat = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

@Composable
fun SavedPointPropertiesCard(
    point: SavedPoint,
    onRename: (String) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var renameDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(point.name) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.6f, %.6f", point.latitude, point.longitude),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Text(
                        text = "✕",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { newName = point.name; renameDialogVisible = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Zmień nazwę", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Podziel się punktem", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { deleteDialogVisible = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Usuń punkt", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text("Zmień nazwę") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Zapisany punkt") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameDialogVisible = false
                        if (newName.isNotBlank()) onRename(newName.trim())
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Usunąć punkt?") },
            text = { Text("Czy na pewno chcesz usunąć punkt „${point.name}”?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        onDelete()
                    }
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}
