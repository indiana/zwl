package com.indiana.zwl.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.indiana.zwl.domain.model.LocationStatus
import com.indiana.zwl.presentation.theme.ZwlTheme
import com.indiana.zwl.presentation.theme.DarkForestBackground
import com.indiana.zwl.presentation.theme.ForestGreenAccent
import com.indiana.zwl.presentation.theme.ForestGreenText
import com.indiana.zwl.presentation.theme.ErrorDarkBackground
import com.indiana.zwl.presentation.theme.ErrorRedAccent
import com.indiana.zwl.presentation.theme.ErrorRedText
import com.indiana.zwl.presentation.theme.ErrorRedButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.indiana.zwl.presentation.map.MapViewContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map

@Composable
fun MainScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val debugError by viewModel.debugError.collectAsStateWithLifecycle()

    debugError?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearDebugError() },
            title = { Text("Błąd Debugowania (Crash Log)") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = errorMsg,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDebugError() }) {
                    Text("Zamknij")
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        viewModel.setLocationPermissionGranted(fineGranted || coarseGranted)
    }

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setLocationPermissionGranted(fineGranted || coarseGranted)
    }

    when (val state = uiState) {
        is MainUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkForestBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = ForestGreenAccent,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Legalny Bushcraft",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenAccent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Inicjalizacja silnika przestrzennego i lokalizacji...",
                        fontSize = 14.sp,
                        color = ForestGreenText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is MainUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ErrorDarkBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Wystąpił błąd",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRedAccent
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        fontSize = 15.sp,
                        color = ErrorRedText,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.retryDatabaseLoad() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRedButton),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Spróbuj ponownie", color = Color.White)
                    }
                }
            }
        }

        is MainUiState.PermissionsRequired -> {
            PermissionsScreen(onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            })
        }

        is MainUiState.EmptyDatabaseRequired -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ErrorDarkBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Brak Danych Lokalnych",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRedAccent
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Brak dostępu do internetu i brak zapisanych lokalnie danych o strefach programu „Zanocuj w Lesie”.\n\nPołącz się z siecią i spróbuj ponownie, aby pobrać wymaganą bazę danych stref.",
                        fontSize = 15.sp,
                        color = ErrorRedText,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.retryDatabaseLoad() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRedButton),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Spróbuj ponownie", color = Color.White)
                    }
                }
            }
        }

        is MainUiState.Success -> {
            val isInZone = state.locationStatus is LocationStatus.InZone
            var selectedTab by rememberSaveable { mutableStateOf(0) }

            ZwlTheme(isInZone = isInZone) {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Status"
                                    )
                                },
                                label = { Text("Status", fontWeight = FontWeight.Bold) }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = "Mapa"
                                    )
                                },
                                label = { Text("Mapa", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (selectedTab == 0) 1f else 0f)
                                .then(if (selectedTab == 0) Modifier else Modifier.size(0.dp))
                        ) {
                            when (val status = state.locationStatus) {
                                is LocationStatus.InZone -> {
                                    InZoneContent(
                                        forestDistrict = status.forestDistrict,
                                        fireRiskLevel = state.fireRiskLevel
                                    )
                                }

                                is LocationStatus.OutsideZone -> {
                                    OutsideZoneContent(
                                        nearestDistrict = status.nearestDistrict,
                                        distanceMeters = status.distanceMeters,
                                        bearingDegrees = status.bearingDegrees,
                                        azimuth = state.azimuth
                                    )
                                }

                                is LocationStatus.EmptyData -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(DarkForestBackground)
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = ForestGreenAccent,
                                                strokeWidth = 3.dp,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Ustalanie lokalizacji GPS...",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = ForestGreenAccent,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Aplikacja oczekuje na pierwsze współrzędne z Twojego urządzenia. Upewnij się, że funkcja lokalizacji (GPS) jest włączona.",
                                                fontSize = 13.sp,
                                                color = ForestGreenText,
                                                textAlign = TextAlign.Center,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(if (selectedTab == 1) 1f else 0f)
                                .then(if (selectedTab == 1) Modifier else Modifier.size(0.dp))
                        ) {
                            MapViewContainer(
                                viewModel = viewModel,
                                zones = viewModel.zones,
                                isActive = selectedTab == 1
                            )
                        }
                    }
                }
            }
        }
    }
}
