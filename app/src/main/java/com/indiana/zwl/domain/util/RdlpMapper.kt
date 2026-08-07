package com.indiana.zwl.domain.util

import com.indiana.zwl.domain.model.TranslatedCode

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
        "14" to "RDLP_Zielona_Gora_wydzielenia",
        "15" to "RDLP_Gdansk_wydzielenia",
        "16" to "RDLP_Radom_wydzielenia",
        "17" to "RDLP_Warszawa_wydzielenia"
    )

    fun collectionForRegionCode(regionCode: String): String? {
        return regionCodeToCollection[regionCode]
    }

    internal fun normalize(code: String): String {
        return code.uppercase()
            .replace("Ś", "S").replace("Ź", "Z").replace("Ż", "Z")
            .replace("Ą", "A").replace("Ć", "C").replace("Ę", "E")
            .replace("Ł", "L").replace("Ń", "N").replace("Ó", "O")
            .replace(" ", "").replace(".", "")
    }

    fun speciesCodeToName(code: String): String {
        return when (normalize(code)) {
            // ---- BDL species_cd (47 authoritative values) ----
            "BK" -> "Buk"
            "BRZ" -> "Brzoza"
            "DB" -> "Dąb"
            "DBS" -> "Dąb szypułkowy"               // DB.S
            "DBB" -> "Dąb bezszypułkowy"            // DB.B
            "DBC" -> "Dąb czerwony"                 // DB.C
            "GB" -> "Grab"
            "JS" -> "Jesion"
            "KL" -> "Klon"
            "KLP" -> "Klon polny"                   // KL.P
            "MD" -> "Modrzew"
            "OL" -> "Olcha"
            "OLS" -> "Olcha szara"                  // OL.S
            "OS" -> "Osika"
            "SO" -> "Sosna"
            "SOS" -> "Sosna smołowa"                // SO.S
            "SW" -> "Świerk"                        // ŚW
            "AK" -> "Akacja (robinia akacjowa)"
            "BRZO" -> "Brzoza omszona"              // BRZ.O
            "CZR" -> "Czereśnia"
            "CZRP" -> "Czereśnia ptasia"            // CZR.P
            "DG" -> "Daglezja"
            "GR" -> "Grusza pospolita"
            "IWA" -> "Wierzba iwa"
            "JD" -> "Jodła"
            "JDJ" -> "Jodła jednobarwna"            // JD.J
            "JDO" -> "Jodła olbrzymia"              // JD.O
            "JKL" -> "Klon jesionolistny"
            "JRZ" -> "Jarząb pospolity"
            "JRZB" -> "Jarząb brekinia"             // JRZ.B
            "JSA" -> "Jesion amerykański"           // JS.A
            "JSP" -> "Jesion pensylwański"          // JS.P
            "LP" -> "Lipa"
            "ORZC" -> "Orzech czarny"               // ORZ.C
            "SOB" -> "Sosna banksa"                 // SO.B
            "SOC" -> "Sosna czarna"                 // SO.C
            "SOK" -> "Sosna kosodrzewina"           // SO.K
            "SOWE" -> "Sosna wejmutka"              // SO.WE
            "TP" -> "Topola"
            "TPM" -> "Topola mieszańcowa"           // TP.M
            "WB" -> "Wierzba"
            "WZ" -> "Wiąz"
            "WZS" -> "Wiąz szypułkowy"              // WZ.S
            "SWKB" -> "Świerk kaukaski"             // ŚW.KB
            "SWSR" -> "Świerk srebrzysty"           // ŚW.SR
            "ZYWO" -> "Żywotnik olbrzymi"           // ŻYW.O
            "JW" -> "Jawor (klon jawor)"
            // ---- legacy aliases (non-colliding, kept for safety) ----
            "SOSZ" -> "Sosna"
            "SWY", "SWR" -> "Świerk"
            "DAB" -> "Dąb"
            "BUK" -> "Buk"
            "BR" -> "Brzoza"
            "JK", "JOD" -> "Jodła"
            "OSI" -> "Osika"
            "WBR" -> "Wiąz"
            "JES" -> "Jesion"
            "SWK" -> "Świerk kraiński"
            "LSC" -> "Leszczyna"
            "TM", "TOP" -> "Topola"
            "LB" -> "Lipa"
            "CIS" -> "Cis"
            "CHJ", "CHO" -> "Choina"
            "MOD" -> "Modrzew"
            "DAG" -> "Daglezja"
            "TWR" -> "Tuja"
            else -> code
        }
    }

    fun forestFunCodeToValue(code: String): TranslatedCode {
        val name = when (normalize(code)) {
            "GOSP" -> "Gospodarczy"
            "OSPO" -> "Lasy szczególnie chronione (ochronne)"   // O SPO
            "REZ" -> "Rezerwatowy"
            "REZCZ" -> "Rezerwat częściowy"                     // REZ CZ
            "REZS" -> "Rezerwat ścisły"                         // REZ Ś
            // legacy aliases (different code system, kept for safety)
            "OCH" -> "Ochronny"
            "TOW" -> "Towarowy"
            "REK" -> "Rekreacyjny"
            "URB" -> "Urbanizacyjny"
            "IZO" -> "Izolacyjny"
            "WKW" -> "Wodochronny"
            "TUR" -> "Turystyczny"
            else -> code
        }
        return TranslatedCode(code = code, name = name)
    }

    fun standStruCodeToValue(code: String): TranslatedCode {
        val name = when (normalize(code)) {
            "2PIET" -> "Dwupiętrowy"                            // 2 PIĘT
            "DRZ", "DRZEWIASTY", "DRZEW" -> "Drzewostan (jednopiętrowy)"
            "KDO" -> "W klasie do odnowienia"
            "KO" -> "W klasie odnowienia"
            "SP" -> "O budowie przerębowej"
            "WPIET" -> "Wielopiętrowy"                          // W PIĘT
            // legacy aliases ("cecha drzewostanu" - a different field, kept for safety)
            "JED" -> "Jednolity wiekowo"
            "RZW" -> "Różnowiekowy"
            "TRW" -> "Trójwarstwowy"
            "GLO", "GLOWNY" -> "Główny"
            else -> code
        }
        return TranslatedCode(code = code, name = name)
    }

    fun siteTypeCodeToValue(code: String): TranslatedCode {
        val name = when (normalize(code)) {
            "BB" -> "Bór bagienny"
            "BGB" -> "Bór górski bagienny"
            "BGSW" -> "Bór górski świeży"                        // BGŚW
            "BGW" -> "Bór górski wilgotny"
            "BMB" -> "Bór mieszany bagienny"
            "BMGB" -> "Bór mieszany górski bagienny"
            "BMGSW" -> "Bór mieszany górski świeży"              // BMGŚW
            "BMGW" -> "Bór mieszany górski wilgotny"
            "BMSW" -> "Bór mieszany świeży"                      // BMŚW
            "BMW" -> "Bór mieszany wilgotny"
            "BMWYZ" -> "Bór mieszany wyżynny świeży"             // BMWYŻ
            "BMWYZW" -> "Bór mieszany wyżynny wilgotny"          // BMWYŻW
            "BS" -> "Bór suchy"
            "BSW" -> "Bór świeży"                                // BŚW
            "BW" -> "Bór wilgotny"
            "BWG" -> "Bór wysokogórski"
            "LGSW" -> "Las górski świeży"                        // LGŚW
            "LGW" -> "Las górski wilgotny"
            "LMB" -> "Las mieszany bagienny"
            "LMG" -> "Las mieszany górski świeży"
            "LMGSW" -> "Las mieszany górski świeży"              // LMGŚW
            "LMGW" -> "Las mieszany górski wilgotny"
            "LMSW" -> "Las mieszany świeży"                      // LMŚW
            "LMW" -> "Las mieszany wilgotny"
            "LMWYZ" -> "Las mieszany wyżynny świeży"             // LMWYŻ
            "LMWYZW" -> "Las mieszany wyżynny wilgotny"          // LMWYŻW
            "LSW" -> "Las świeży"                                // LŚW
            "LW" -> "Las wilgotny"
            "LWYZ" -> "Las wyżynny świeży"                       // LWYŻ
            "LWYZS" -> "Las wyżynny świeży"                      // LWYŻŚ
            "LWYZW" -> "Las wyżynny wilgotny"                    // LWYŻW
            "LL" -> "Las łęgowy"                                 // LŁ
            "LLG" -> "Las łęgowy górski"                         // LŁG
            "LLWYZ" -> "Las łęgowy wyżynny"                      // LŁWYŻ
            "OL" -> "Ols"
            "OLJ" -> "Ols jesionowy"
            "OLJG" -> "Ols jesionowy górski"
            "OLJWYZ" -> "Ols jesionowy wyżynny"                  // OLJWYŻ
            // full-form variants occasionally emitted by the API (simplified forms above)
            "BMWYZSW" -> "Bór mieszany wyżynny świeży"           // BMWYŻŚW
            "LMWYZSW" -> "Las mieszany wyżynny świeży"           // LMWYŻŚW
            "LWYZSW" -> "Las wyżynny świeży"                     // LWYŻŚW
            else -> code
        }
        return TranslatedCode(code = code, name = name)
    }

    fun protCategCodeToValue(code: String): TranslatedCode {
        val name = when (normalize(code)) {
            "OCHBADAW" -> "Powierzchnie badawczo-doświadczalne"  // OCH BADAW
            "OCHCENNE", "CENNE" -> "Cenne fragmenty przyrody"    // OCH CENNE
            "OCHGLEB" -> "Glebochronne"                          // OCH GLEB
            "OCHMIAST", "MIAST", "OCHMIASTOWA" -> "Lasy w granicach miast" // OCH MIAST
            "OCHNAS" -> "Nasienne"                               // OCH NAS
            "OCHOBR" -> "Obronne"                                // OCH OBR
            "OCHOSTOJ" -> "Ostoje zwierząt"                      // OCH OSTOJ
            "OCHUSZK" -> "Trwale uszkodzone (przez przemysł)"    // OCH USZK
            "OCHUZDR" -> "Uzdrowiskowe"                          // OCH UZDR
            "OCHWOD", "WOD" -> "Wodochronne"                     // OCH WOD
            // legacy aliases (kept for safety)
            "OCHSCISLE", "SCISLE" -> "Ochrona ścisła"
            "OCHPSTREFOWA", "PSTREFOWA" -> "Ochrona częściowa"
            "SPECJALNE" -> "Specjalne"
            "OCHNADWODNA", "NADWODNA" -> "Ochrona nadwodna"
            "OCHKRAJOBRAZOWA", "KRAJOBRAZOWA" -> "Ochrona krajobrazowa"
            else -> code
        }
        return TranslatedCode(code = code, name = name)
    }
}
