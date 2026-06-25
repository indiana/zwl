package com.indiana.zwl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.presentation.theme.DarkForestBackground
import com.indiana.zwl.presentation.theme.DarkForestSurface
import com.indiana.zwl.presentation.theme.ForestGreenSubtext
import com.indiana.zwl.presentation.theme.GreenPrimary

@Composable
fun PermissionsScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkForestBackground),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkForestSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Wymagane Uprawnienia Lokalizacyjne",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Aplikacja \"Zanocuj w Lesie\" wymaga dostępu do precyzyjnej lokalizacji GPS w celu sprawdzania czy znajdujesz się w legalnej strefie biwakowania oraz do nawigacji kompasem offline w terenie.",
                    fontSize = 14.sp,
                    color = ForestGreenSubtext,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zezwól na dostęp", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
