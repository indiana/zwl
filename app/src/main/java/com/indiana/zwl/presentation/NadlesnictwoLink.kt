package com.indiana.zwl.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.domain.util.NadlesnictwoUrls
import com.indiana.zwl.presentation.theme.ForestGreenAccent

fun openNadlesnictwoWebsite(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure { /* no browser / no handler: silently ignore */ }
}

@Composable
fun WebsiteLinkRow(
    url: String,
    label: String = "Strona internetowa:",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val host = remember(url) { NadlesnictwoUrls.displayHost(url) } ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { openNadlesnictwoWebsite(context, url) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = host,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ForestGreenAccent,
                textDecoration = TextDecoration.Underline
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Otwórz stronę nadleśnictwa",
                tint = ForestGreenAccent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
