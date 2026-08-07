package com.indiana.zwl.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RdlpMapperTest {

    // Canonical 47 raw BDL species codes (identical to the authoritative tables / BdlInfo keys).
    private val speciesCodes = listOf(
        "SO", "ŚW", "DB", "DB.S", "DB.B", "DB.C", "BK", "BRZ", "BRZ.O", "JD", "JD.O", "JD.J",
        "OL", "OL.S", "OS", "WB", "WZ", "WZ.S", "JS", "JS.A", "JS.P", "KL", "KL.P", "JKL",
        "JW", "LP", "MD", "DG", "GB", "AK", "CZR", "CZR.P", "GR", "JRZ", "JRZ.B", "ORZ.C",
        "SO.B", "SO.C", "SO.K", "SO.S", "SO.WE", "TP", "TP.M", "ŚW.KB", "ŚW.SR", "ŻYW.O", "IWA"
    )

    // 38 site types + 3 full-form variants emitted by the API.
    private val siteTypeCodes = listOf(
        "BB", "BGB", "BGŚW", "BGW", "BMB", "BMGB", "BMGŚW", "BMGW", "BMŚW", "BMW",
        "BMWYŻ", "BMWYŻW", "BS", "BŚW", "BW", "BWG", "LGŚW", "LGW", "LMB", "LMG",
        "LMGŚW", "LMGW", "LMŚW", "LMW", "LMWYŻ", "LMWYŻW", "LŚW", "LW", "LWYŻ", "LWYŻŚ",
        "LWYŻW", "LŁ", "LŁG", "LŁWYŻ", "OL", "OLJ", "OLJG", "OLJWYŻ",
        "BMWYŻŚW", "LMWYŻŚW", "LWYŻŚW"
    )

    private val forestFunCodes = listOf("GOSP", "O SPO", "REZ", "REZ CZ", "REZ Ś")

    private val standStruCodes = listOf("2 PIĘT", "DRZEW", "KDO", "KO", "SP", "W PIĘT")

    private val protCategCodes = listOf(
        "OCH BADAW", "OCH CENNE", "OCH GLEB", "OCH MIAST", "OCH NAS",
        "OCH OBR", "OCH OSTOJ", "OCH USZK", "OCH UZDR", "OCH WOD"
    )

    @Test
    fun `speciesCodeToName translates all 47 BDL species codes`() {
        assertEquals(47, speciesCodes.size)
        speciesCodes.forEach { code ->
            val name = RdlpMapper.speciesCodeToName(code)
            assertNotEquals("speciesCodeToName($code) must return a translated name", code, name)
        }
    }

    @Test
    fun `speciesCodeToName spot-checks the fixes`() {
        assertEquals("Jawor (klon jawor)", RdlpMapper.speciesCodeToName("JW"))
        assertEquals("Wierzba", RdlpMapper.speciesCodeToName("WB"))
        assertEquals("Dąb czerwony", RdlpMapper.speciesCodeToName("DB.C"))
        assertEquals("Dąb bezszypułkowy", RdlpMapper.speciesCodeToName("DB.B"))
        assertEquals("Dąb szypułkowy", RdlpMapper.speciesCodeToName("DB.S"))
        assertEquals("Klon polny", RdlpMapper.speciesCodeToName("KL.P"))
        assertEquals("Olcha szara", RdlpMapper.speciesCodeToName("OL.S"))
        assertEquals("Sosna smołowa", RdlpMapper.speciesCodeToName("SO.S"))
        assertEquals("Świerk", RdlpMapper.speciesCodeToName("ŚW"))
        assertEquals("Żywotnik olbrzymi", RdlpMapper.speciesCodeToName("ŻYW.O"))
    }

    @Test
    fun `siteTypeCodeToValue translates all 38 site types and 3 full-form variants`() {
        assertEquals(41, siteTypeCodes.size)
        siteTypeCodes.forEach { code ->
            val value = RdlpMapper.siteTypeCodeToValue(code)
            assertNotEquals("siteTypeCodeToValue($code) must return a translated name", code, value.name)
            assertEquals("siteTypeCodeToValue($code) must preserve the raw code", code, value.code)
        }
    }

    @Test
    fun `forestFunCodeToValue translates all 5 forest functions`() {
        assertEquals(5, forestFunCodes.size)
        forestFunCodes.forEach { code ->
            val value = RdlpMapper.forestFunCodeToValue(code)
            assertNotEquals("forestFunCodeToValue($code) must return a translated name", code, value.name)
            assertEquals("forestFunCodeToValue($code) must preserve the raw code", code, value.code)
        }
    }

    @Test
    fun `standStruCodeToValue translates all 6 stand structures`() {
        assertEquals(6, standStruCodes.size)
        standStruCodes.forEach { code ->
            val value = RdlpMapper.standStruCodeToValue(code)
            assertNotEquals("standStruCodeToValue($code) must return a translated name", code, value.name)
            assertEquals("standStruCodeToValue($code) must preserve the raw code", code, value.code)
        }
    }

    @Test
    fun `protCategCodeToValue translates all 10 protection categories`() {
        assertEquals(10, protCategCodes.size)
        protCategCodes.forEach { code ->
            val value = RdlpMapper.protCategCodeToValue(code)
            assertNotEquals("protCategCodeToValue($code) must return a translated name", code, value.name)
            assertEquals("protCategCodeToValue($code) must preserve the raw code", code, value.code)
        }
    }

    @Test
    fun `diacritic space and dot tolerance`() {
        assertEquals("Bór mieszany wyżynny świeży", RdlpMapper.siteTypeCodeToValue("BMWYŻŚW").name)
        assertEquals("Lasy szczególnie chronione (ochronne)", RdlpMapper.forestFunCodeToValue("O SPO").name)
        assertEquals("Dwupiętrowy", RdlpMapper.standStruCodeToValue("2 PIĘT").name)
        assertEquals("Rezerwat ścisły", RdlpMapper.forestFunCodeToValue("REZ Ś").name)
        assertEquals("Lasy w granicach miast", RdlpMapper.protCategCodeToValue("OCH MIAST").name)
    }

    @Test
    fun `unknown codes fall back to the raw code`() {
        assertEquals("ZZZ", RdlpMapper.siteTypeCodeToValue("ZZZ").name)
        assertEquals("ZZZ", RdlpMapper.forestFunCodeToValue("ZZZ").name)
        assertEquals("ZZZ", RdlpMapper.standStruCodeToValue("ZZZ").name)
        assertEquals("ZZZ", RdlpMapper.protCategCodeToValue("ZZZ").name)
        assertEquals("ZZZ", RdlpMapper.speciesCodeToName("ZZZ"))
    }

    @Test
    fun `region map includes fixed Zielona Gora and new Radom entries`() {
        assertEquals("RDLP_Zielona_Gora_wydzielenia", RdlpMapper.collectionForRegionCode("14"))
        assertEquals("RDLP_Radom_wydzielenia", RdlpMapper.collectionForRegionCode("16"))
    }
}
