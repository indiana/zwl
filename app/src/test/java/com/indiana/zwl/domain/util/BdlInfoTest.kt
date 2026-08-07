package com.indiana.zwl.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BdlInfoTest {

    // Canonical 47 raw BDL species codes (identical to the authoritative tables / RdlpMapper).
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
    fun `wikipediaTitleForSpecies returns a title for all 47 BDL species codes`() {
        assertEquals(47, speciesCodes.size)
        speciesCodes.forEach { code ->
            val title = BdlInfo.wikipediaTitleForSpecies(code)
            assertNotNull("no Wikipedia title for species code $code", title)
            assertTrue("blank Wikipedia title for species code $code", title!!.isNotBlank())
        }
    }

    @Test
    fun `wikipediaTitleForSpecies spot-checks`() {
        assertEquals("Sosna zwyczajna", BdlInfo.wikipediaTitleForSpecies("SO"))
        assertEquals("Klon jawor", BdlInfo.wikipediaTitleForSpecies("JW"))
        assertEquals("Wierzba", BdlInfo.wikipediaTitleForSpecies("WB"))
        assertEquals("Dąb czerwony", BdlInfo.wikipediaTitleForSpecies("DB.C"))
        assertEquals("Żywotnik olbrzymi", BdlInfo.wikipediaTitleForSpecies("ŻYW.O"))
        assertEquals("Świerk kaukaski", BdlInfo.wikipediaTitleForSpecies("ŚW.KB"))
        assertEquals("Świerk kłujący", BdlInfo.wikipediaTitleForSpecies("ŚW.SR"))
    }

    @Test
    fun `tooltips exist for all canonical metadata codes`() {
        siteTypeCodes.forEach { code ->
            val tooltip = BdlInfo.tooltipForSiteType(code)
            assertNotNull("no tooltip for site type $code", tooltip)
            assertTrue("blank tooltip for site type $code", tooltip!!.isNotBlank())
        }
        forestFunCodes.forEach { code ->
            val tooltip = BdlInfo.tooltipForForestFun(code)
            assertNotNull("no tooltip for forest function $code", tooltip)
            assertTrue("blank tooltip for forest function $code", tooltip!!.isNotBlank())
        }
        standStruCodes.forEach { code ->
            val tooltip = BdlInfo.tooltipForStandStru(code)
            assertNotNull("no tooltip for stand structure $code", tooltip)
            assertTrue("blank tooltip for stand structure $code", tooltip!!.isNotBlank())
        }
        protCategCodes.forEach { code ->
            val tooltip = BdlInfo.tooltipForProtCateg(code)
            assertNotNull("no tooltip for protection category $code", tooltip)
            assertTrue("blank tooltip for protection category $code", tooltip!!.isNotBlank())
        }
    }

    @Test
    fun `full-form variants and space containing codes resolve`() {
        assertNotNull(BdlInfo.tooltipForSiteType("BMWYŻŚW"))
        assertNotNull(BdlInfo.tooltipForSiteType("LMWYŻŚW"))
        assertNotNull(BdlInfo.tooltipForSiteType("LWYŻŚW"))
        assertNotNull(BdlInfo.tooltipForStandStru("2 PIĘT"))
        assertNotNull(BdlInfo.tooltipForForestFun("O SPO"))
        assertNotNull(BdlInfo.tooltipForProtCateg("OCH MIAST"))
    }

    @Test
    fun `rotation age generic tooltip is present`() {
        assertNotNull(BdlInfo.rotationAgeTooltip)
        assertTrue(BdlInfo.rotationAgeTooltip.isNotBlank())
    }

    @Test
    fun `unknown codes return null`() {
        assertNull(BdlInfo.wikipediaTitleForSpecies("ZZZ"))
        assertNull(BdlInfo.tooltipForSiteType("ZZZ"))
        assertNull(BdlInfo.tooltipForForestFun("ZZZ"))
        assertNull(BdlInfo.tooltipForStandStru("ZZZ"))
        assertNull(BdlInfo.tooltipForProtCateg("ZZZ"))
    }

    @Test
    fun `consistency guard - no drift between RdlpMapper translations and BdlInfo lookups`() {
        // every canonical species code is translated by RdlpMapper AND has a Wikipedia title
        speciesCodes.forEach { code ->
            val name = RdlpMapper.speciesCodeToName(code)
            val title = BdlInfo.wikipediaTitleForSpecies(code)
            assertTrue(
                "species code $code missing translation or Wikipedia title",
                name != code && title != null
            )
        }
        // every canonical metadata code is translated AND has a tooltip
        siteTypeCodes.forEach { code ->
            val value = RdlpMapper.siteTypeCodeToValue(code)
            assertTrue(
                "site type $code missing translation or tooltip",
                value.name != code && BdlInfo.tooltipForSiteType(code) != null
            )
        }
        forestFunCodes.forEach { code ->
            val value = RdlpMapper.forestFunCodeToValue(code)
            assertTrue(
                "forest function $code missing translation or tooltip",
                value.name != code && BdlInfo.tooltipForForestFun(code) != null
            )
        }
        standStruCodes.forEach { code ->
            val value = RdlpMapper.standStruCodeToValue(code)
            assertTrue(
                "stand structure $code missing translation or tooltip",
                value.name != code && BdlInfo.tooltipForStandStru(code) != null
            )
        }
        protCategCodes.forEach { code ->
            val value = RdlpMapper.protCategCodeToValue(code)
            assertTrue(
                "protection category $code missing translation or tooltip",
                value.name != code && BdlInfo.tooltipForProtCateg(code) != null
            )
        }
    }
}
