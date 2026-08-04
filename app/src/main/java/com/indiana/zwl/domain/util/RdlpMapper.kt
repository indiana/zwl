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

    fun speciesCodeToName(code: String): String {
        return when (code.uppercase().replace(".", "").replace(" ", "")) {
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
            "DW" -> "Dąb bezszypułkowy"
            else -> code
        }
    }

    fun siteTypeCodeToName(code: String): String {
        return when (code.uppercase().replace("Ś", "S").replace("Ł", "L").replace("Ń", "N")) {
            "BMWC", "BWMC" -> "Bór wilgotny z traworoślnymi"
            "BMPP", "BMP" -> "Bór bagienny"
            "BMSW", "BMS" -> "Bór świeży"
            "BMRC", "BMR" -> "Bórsuchy z wrzosem"
            "LMSC", "LMS" -> "Las mieszany świeży"
            "LMRC", "LMR" -> "Las mieszany suchy"
            "LMWC", "LMW" -> "Las mieszany wilgotny"
            "LMBC", "LMB" -> "Las mieszany bagienny"
            "LSW", "LS" -> "Las świeży"
            "LRC", "LR" -> "Las suchy"
            "LWC", "LW" -> "Las wilgotny"
            "LBC", "LB" -> "Las bagienny"
            "OLSW", "OLS" -> "Olszyny wilgotne"
            "OLBC", "OLB" -> "Olszyny bagienne"
            else -> code
        }
    }

    fun forestFunCodeToName(code: String): String {
        return when (code.uppercase()) {
            "GOSP" -> "Gospodarczy"
            "OCH" -> "Ochronny"
            "TOW" -> "Towarowy"
            "REK" -> "Rekreacyjny"
            "URB", "URBNIZACYJNY" -> "Urbanizacyjny"
            "IZO" -> "Izolacyjny"
            "WKW" -> "Wodochronny"
            "TUR" -> "Turystyczny"
            else -> code
        }
    }

    fun standStruCodeToName(code: String): String {
        return when (code.uppercase()) {
            "JED" -> "Jednolity wiekowo"
            "RZW" -> "Różnowiekowy"
            "TRW" -> "Trójwarstwowy"
            "DRZ", "DRZEWIASTY" -> "Drzewiasty"
            "GLO", "GLOWNY" -> "Główny"
            "PL" -> "Płożący"
            "BOR" -> "Borowy"
            "OLS" -> "Olsowy"
            "LSC" -> "Łęgowy"
            "MCH" -> "Mechowiskowy"
            "WRZ" -> "Wrzosowiskowy"
            else -> code
        }
    }

    fun protCategCodeToName(code: String): String {
        return when (code.uppercase()) {
            "OCH CENNE", "CENNE" -> "Ochrona czynna"
            "OCH SCISLE", "SCISLE" -> "Ochrona ścisła"
            "OCH PSTREFOWA", "PSTREFOWA" -> "Ochrona częściowa"
            "SPECJALNE" -> "Specjalne"
            "OCH MIAST", "MIAST" -> "Ochrona urbanistyczna"
            "OCH NADWODNA", "NADWODNA" -> "Ochrona nadwodna"
            "OCH KRAJOBRAZOWA", "KRAJOBRAZOWA" -> "Ochrona krajobrazowa"
            else -> code
        }
    }
}
