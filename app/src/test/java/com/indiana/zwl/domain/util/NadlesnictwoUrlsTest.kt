package com.indiana.zwl.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NadlesnictwoUrlsTest {

    @Test
    fun `single word name builds joined host`() {
        val url = NadlesnictwoUrls.websiteUrl("Kościan", "Poznań")
        assertEquals("https://koscian.poznan.lasy.gov.pl", url)
    }

    @Test
    fun `multi word name defaults to joined slug`() {
        val url = NadlesnictwoUrls.websiteUrl("Bory Tucholskie", "Toruń")
        assertEquals("https://borytucholskie.torun.lasy.gov.pl", url)
    }

    @Test
    fun `two city RDLP is joined without separator`() {
        val url = NadlesnictwoUrls.websiteUrl("Babimost", "Zielona Góra")
        assertEquals("https://babimost.zielonagora.lasy.gov.pl", url)
    }

    @Test
    fun `rdlp prefix is stripped`() {
        val url = NadlesnictwoUrls.websiteUrl("Kościan", "RDLP Poznań")
        assertEquals("https://koscian.poznan.lasy.gov.pl", url)
    }

    @Test
    fun `district prefix is stripped`() {
        val url = NadlesnictwoUrls.websiteUrl("Nadleśnictwo Kudypy", "Szczecinek")
        assertEquals("https://kudypy.szczecinek.lasy.gov.pl", url)
    }

    @Test
    fun `hyphenated overrides are applied`() {
        val cases = mapOf(
            "Czarna Białostocka" to "czarna-bialostocka.bialystok",
            "Lesny Dwór" to "lesny-dwor.szczecinek",
            "Solec Kujawski" to "solec-kujawski.torun"
        )
        for ((name, host) in cases) {
            val url = NadlesnictwoUrls.websiteUrl(name, "RDLP Warszawa")
            assertEquals("https://$host.lasy.gov.pl", url)
        }
    }

    @Test
    fun `special overrides are applied`() {
        assertEquals(
            "https://nadlesnictwo.gdansk.lasy.gov.pl",
            NadlesnictwoUrls.websiteUrl("Gdańsk", "Gdańsk")
        )
        assertEquals(
            "https://dabrowa.krakow.lasy.gov.pl",
            NadlesnictwoUrls.websiteUrl("Dąbrowa Tarnowska", "Kraków")
        )
        assertEquals(
            "https://ostrow.warszawa.lasy.gov.pl",
            NadlesnictwoUrls.websiteUrl("Ostrów Mazowiecka", "Warszawa")
        )
        assertEquals(
            "https://ostrowiec.radom.lasy.gov.pl",
            NadlesnictwoUrls.websiteUrl("Ostrowiec Świętokrzyski", "Radom")
        )
    }

    @Test
    fun `returns null when inputs are missing or blank`() {
        assertNull(NadlesnictwoUrls.websiteUrl(null, "Poznań"))
        assertNull(NadlesnictwoUrls.websiteUrl("Kościan", null))
        assertNull(NadlesnictwoUrls.websiteUrl("   ", "Poznań"))
        assertNull(NadlesnictwoUrls.websiteUrl("Kościan", "   "))
        assertNull(NadlesnictwoUrls.websiteUrl("Kościan", "RDLP"))
    }

    @Test
    fun `displayHost strips scheme`() {
        assertEquals(
            "koscian.poznan.lasy.gov.pl",
            NadlesnictwoUrls.displayHost("https://koscian.poznan.lasy.gov.pl")
        )
        assertNull(NadlesnictwoUrls.displayHost(null))
    }
}
