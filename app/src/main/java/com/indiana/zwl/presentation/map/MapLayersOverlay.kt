package com.indiana.zwl.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    text = "Wyświetlanie na mapie",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f), thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                LayerCheckboxRow(
                    checked = showOwnPoints,
                    color = Color(0xFFE91E63),
                    label = "Własne punkty",
                    onCheckedChange = onShowOwnPointsChange
                )
                LayerCheckboxRow(
                    checked = showForestBans,
                    color = MaterialTheme.colorScheme.error,
                    label = "Zakazy wstępu do lasu",
                    onCheckedChange = onShowForestBansChange
                )
                LayerCheckboxRow(
                    checked = showAccommodation,
                    color = Color(0xFF1B5E20),
                    label = "Noclegi i biwakowanie",
                    onCheckedChange = onShowAccommodationChange
                )
                LayerCheckboxRow(
                    checked = showRest,
                    color = Color(0xFF558B2F),
                    label = "Miejsca wypoczynku",
                    onCheckedChange = onShowRestChange
                )
                LayerCheckboxRow(
                    checked = showShelters,
                    color = Color(0xFF4E342E),
                    label = "Wiaty i schronienia",
                    onCheckedChange = onShowSheltersChange
                )
                LayerCheckboxRow(
                    checked = showFireplaces,
                    color = Color(0xFFE65100),
                    label = "Miejsca na ognisko",
                    onCheckedChange = onShowFireplacesChange
                )
                LayerCheckboxRow(
                    checked = showViewpoints,
                    color = Color(0xFF0097A7),
                    label = "Punkty widokowe i rekreacja",
                    onCheckedChange = onShowViewpointsChange
                )
                LayerCheckboxRow(
                    checked = showParking,
                    color = Color(0xFF5D4037),
                    label = "Parkingi",
                    onCheckedChange = onShowParkingChange
                )
                LayerCheckboxRow(
                    checked = showEducation,
                    color = Color(0xFF7B1FA2),
                    label = "Edukacja leśna",
                    onCheckedChange = onShowEducationChange
                )
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