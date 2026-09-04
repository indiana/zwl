package com.indiana.zwl.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indiana.zwl.BuildConfig
import com.indiana.zwl.presentation.theme.DarkForestBackground
import com.indiana.zwl.presentation.theme.ForestGreenAccent
import com.indiana.zwl.presentation.theme.ForestGreenText

private const val BDL_PORTAL_URL = "https://www.bdl.lasy.gov.pl/portal/"
private const val IBL_FIRE_URL = "https://bazapozarow.ibles.pl/"
private const val ZANOCUJ_W_LESIE_URL = "https://www.lasy.gov.pl/pl/turystyka/program-zanocuj-w-lesie"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "O aplikacji",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkForestBackground
                )
            )
        },
        containerColor = DarkForestBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ForestGreenAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zastrzeżenie prawne",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Aplikacja „Legalny Bushcraft” jest niezależnym narzędziem stworzonym przez podmiot prywatny. Nie reprezentuje ona, nie jest powiązana, autoryzowana ani wspierana przez żadną instytucję rządową, państwową ani publiczną, w tym przez Państwowe Gospodarstwo Leśne Lasy Państwowe, Ministerstwo Klimatu i Środowiska czy Bank Danych o Lasach (BDL).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Prezentowane dane mają charakter wyłącznie informacyjny i pomocniczy. Przed wyruszeniem w teren samodzielnie zweryfikuj informacje i przestrzegaj aktualnych regulaminów lokalnych nadleśnictw.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = ForestGreenAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Źródła danych rządowych",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Aplikacja pobiera i prezentuje informacje pochodzące z oficjalnych, publicznych i ogólnodostępnych źródeł:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )

                    SourceLinkRow(label = "Bank Danych o Lasach (BDL)", url = BDL_PORTAL_URL)
                    SourceLinkRow(label = "Zagrożenie pożarowe (IBL)", url = IBL_FIRE_URL)
                    SourceLinkRow(label = "Program „Zanocuj w lesie”", url = ZANOCUJ_W_LESIE_URL)

                    Text(
                        text = "Dane geometryczne stref programu „Zanocuj w lesie” oraz lokalizacje infrastruktury turystycznej pochodzą z serwisu Banku Danych o Lasach. Informacje o stopniu zagrożenia pożarowego opracowywane są przez Instytut Badawczy Leśnictwa.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ForestGreenAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Prywatność",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Twoja lokalizacja służy wyłącznie do sprawdzenia stref i jest przetwarzana lokalnie na urządzeniu. Aby pokazać zagrożenie pożarowe, aplikacja wysyła anonimowe zapytanie z Twoimi współrzędnymi do publicznego API BDL — bez tworzenia profili i bez zapisu historii lokalizacji. Aplikacja nie zawiera reklam ani systemów analitycznych.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 19.sp
                    )
                }
            }

            Text(
                text = "Legalny Bushcraft • wersja ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = ForestGreenText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SourceLinkRow(label: String, url: String) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = url,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ForestGreenAccent,
            textDecoration = TextDecoration.Underline,
            lineHeight = 18.sp,
            modifier = Modifier.clickable { openNadlesnictwoWebsite(context, url) }
        )
    }
}
