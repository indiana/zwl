package com.indiana.zwl.presentation.map

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MapLayersOverlay(
    showOwnPoints: Boolean,
    showForestBans: Boolean,
    showAccommodation: Boolean,
    showRest: Boolean,
    showShelters: Boolean,
    showFireplaces: Boolean,
    showViewpoints: Boolean,
    showParking: Boolean,
    showEducation: Boolean,
    showOthers: Boolean,
    onShowOwnPointsChange: (Boolean) -> Unit,
    onShowForestBansChange: (Boolean) -> Unit,
    onShowAccommodationChange: (Boolean) -> Unit,
    onShowRestChange: (Boolean) -> Unit,
    onShowSheltersChange: (Boolean) -> Unit,
    onShowFireplacesChange: (Boolean) -> Unit,
    onShowViewpointsChange: (Boolean) -> Unit,
    onShowParkingChange: (Boolean) -> Unit,
    onShowEducationChange: (Boolean) -> Unit,
    onShowOthersChange: (Boolean) -> Unit,
    onClose: () -> Unit
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
                        onClick = onClose,
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
                        text = "Wyświetlanie na mapie",
                        fontSize = if (isLandscape) 15.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 1.dp)

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LayerCheckboxRow(
                            checked = showOwnPoints,
                            color = Color(0xFFE91E63),
                            label = "Własne punkty",
                            onCheckedChange = onShowOwnPointsChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showForestBans,
                            color = MaterialTheme.colorScheme.error,
                            label = "Zakazy wstępu do lasu",
                            onCheckedChange = onShowForestBansChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showAccommodation,
                            color = Color(0xFF1B5E20),
                            label = "Noclegi i biwakowanie",
                            onCheckedChange = onShowAccommodationChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showRest,
                            color = Color(0xFF558B2F),
                            label = "Miejsca wypoczynku",
                            onCheckedChange = onShowRestChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showShelters,
                            color = Color(0xFF4E342E),
                            label = "Wiaty i schronienia",
                            onCheckedChange = onShowSheltersChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showFireplaces,
                            color = Color(0xFFE65100),
                            label = "Miejsca na ognisko",
                            onCheckedChange = onShowFireplacesChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showViewpoints,
                            color = Color(0xFF0097A7),
                            label = "Punkty widokowe i rekreacja",
                            onCheckedChange = onShowViewpointsChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showParking,
                            color = Color(0xFF5D4037),
                            label = "Parkingi",
                            onCheckedChange = onShowParkingChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showEducation,
                            color = Color(0xFF7B1FA2),
                            label = "Edukacja leśna",
                            onCheckedChange = onShowEducationChange
                        )
                    }
                    item {
                        LayerCheckboxRow(
                            checked = showOthers,
                            color = Color(0xFF1976D2),
                            label = "Inne punkty",
                            onCheckedChange = onShowOthersChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerCheckboxRow(
    checked: Boolean,
    color: Color,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = color)
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .padding(horizontal = 4.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}