package com.indiana.zwl.domain.util

object RdlpMapper {

    private val regionCodeToCollection: Map<String, String> = mapOf(
        "01" to "RDLP_Bialystok_wydzielenia",
        "02" to "RDLP_Katowice_wydzielenia",
        "03" to "RDLP_Krakow_wydzielenia",
        "04" to "RDLP_Krosno_wydzielenia",
        "05" to "RDLP_Lublin_wydzielenia",
        "06" to "RDLP_Lodz_wydzielenia",
        "07" to "RDLP_Olsztyn_wydzielenia",
        "08" to "RDLP_Pila_wydzielenia",
        "09" to "RDLP_Poznan_wydzielenia",
        "10" to "RDLP_Szczecin_wydzielenia",
        "11" to "RDLP_Szczecinek_wydzielenia",
        "12" to "RDLP_Torun_wydzielenia",
        "13" to "RDLP_Wroclaw_wydzielenia",
        "14" to "RDLP_ZielonaGora_wydzielenia",
        "15" to "RDLP_Gdansk_wydzielenia",
        "17" to "RDLP_Warszawa_wydzielenia"
    )

    fun collectionForRegionCode(regionCode: String): String? {
        return regionCodeToCollection[regionCode]
    }

    private fun normalize(code: String): String {
        return code.uppercase()
            .replace("Ś", "S").replace("Ź", "Z").replace("Ż", "Z")
            .replace("Ą", "A").replace("Ć", "C").replace("Ę", "E")
            .replace("Ł", "L").replace("Ń", "N").replace("Ó", "O")
            .replace(" ", "").replace(".", "")
    }

    fun speciesCodeToName(code: String): String {
        return when (normalize(code)) {
            "SO", "SOS", "SOSZ" -> "Sosna"
            "SW", "SWY", "SWR" -> "Świerk"
            "DB", "DAB", "DBS" -> "Dąb"
            "BK", "BUK" -> "Buk"
            "BR", "BRZ" -> "Brzoza"
            "JK", "JOD" -> "Jodła"
            "OL", "OLS" -> "Olcha"
            "OS", "OSI" -> "Osika"
            "WB", "WBR" -> "Wiąz"
            "JS", "JES" -> "Jesion"
            "KL", "KLP" -> "Klon"
            "SWK" -> "Świerk kraiński"
            "LSC" -> "Leszczyna"
            "TM", "TOP" -> "Topola"
            "LB" -> "Lipa"
            "CIS" -> "Cis"
            "CHJ", "CHO" -> "Choina"
            "MOD", "MD" -> "Modrzew"
            "DAG" -> "Daglezja"
            "TWR" -> "Tuja"
            "JW" -> "Jodła wingska"
            "DBC" -> "Dąb bezszypułkowy"
            "GB" -> "Grab"
            else -> code
        }
    }

    fun siteTypeCodeToName(code: String): String {
        return when (normalize(code)) {
            "BMWC" -> "Bór wilgotny z traworoślnymi"
            "BMPP" -> "Bór bagienny"
            "BMSW" -> "Bór świeży"
            "BMRC" -> "Bórsuchy z wrzosem"
            "LMSC" -> "Las mieszany świeży"
            "LMRC" -> "Las mieszany suchy"
            "LMWC" -> "Las mieszany wilgotny"
            "LMBC" -> "Las mieszany bagienny"
            "LSW" -> "Las świeży"
            "LRC" -> "Las suchy"
            "LWC" -> "Las wilgotny"
            "LBC" -> "Las bagienny"
            "OLSW" -> "Olszyny wilgotne"
            "OLBC" -> "Olszyny bagienne"
            else -> code
        }
    }

    fun forestFunCodeToName(code: String): String {
        return when (normalize(code)) {
            "GOSP" -> "Gospodarczy"
            "OCH" -> "Ochronny"
            "TOW" -> "Towarowy"
            "REK" -> "Rekreacyjny"
            "URB" -> "Urbanizacyjny"
            "IZO" -> "Izolacyjny"
            "WKW" -> "Wodochronny"
            "TUR" -> "Turystyczny"
            else -> code
        }
    }

    fun standStruCodeToName(code: String): String {
        return when (normalize(code)) {
            "JED" -> "Jednolity wiekowo"
            "RZW" -> "Różnowiekowy"
            "TRW" -> "Trójwarstwowy"
            "DRZ", "DRZEWIASTY", "DRZEW" -> "Drzewiasty"
            "GLO", "GLOWNY" -> "Główny"
            else -> code
        }
    }

    fun protCategCodeToName(code: String): String {
        return when (normalize(code)) {
            "OCHCENNE", "CENNE" -> "Ochrona czynna"
            "OCHSCISLE", "SCISLE" -> "Ochrona ścisła"
            "OCHPSTREFOWA", "PSTREFOWA" -> "Ochrona częściowa"
            "SPECJALNE" -> "Specjalne"
            "OCHMIAST", "MIAST", "OCHMIASTOWA" -> "Ochrona urbanistyczna"
            "OCHNADWODNA", "NADWODNA" -> "Ochrona nadwodna"
            "OCHKRAJOBRAZOWA", "KRAJOBRAZOWA" -> "Ochrona krajobrazowa"
            "OCHWOD", "WOD" -> "Ochrona wodna"
            else -> code
        }
    }
}
