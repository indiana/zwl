package com.indiana.zwl.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.indiana.zwl.R

private const val BUYCOFFEE_URL = "https://buycoffee.to/legalny-bushcraft"

@Composable
fun BuyCoffeeButton() {
    val context = LocalContext.current
    Image(
        painter = painterResource(id = R.drawable.buycoffee_button),
        contentDescription = "Wesprzyj projekt w BuyCoffee",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(220.dp)
            .height(58.dp)
            .clickable { openNadlesnictwoWebsite(context, BUYCOFFEE_URL) }
    )
}
