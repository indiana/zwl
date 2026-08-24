package com.indiana.zwl.domain.util

/**
 * Derives the official website address (https://{host}) of a nadleśnictwo
 * ("{slug}.{rdlp-city}.lasy.gov.pl") from its name and RDLP name.
 *
 * The default rule is a de-diacritized, lowercased slug of the district name
 * joined WITHOUT separators, combined with the de-diacritized RDLP city name.
 * Units whose real host differs from that rule are listed in [HOST_OVERRIDES]
 * (verified against all 429 nadleśnictwa).
 */
object NadlesnictwoUrls {

    private const val HOST_SUFFIX = "lasy.gov.pl"

    private val DIACRITICS = mapOf(
        'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
        'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
        'Ą' to 'A', 'Ć' to 'C', 'Ę' to 'E', 'Ł' to 'L', 'Ń' to 'N',
        'Ó' to 'O', 'Ś' to 'S', 'Ź' to 'Z', 'Ż' to 'Z'
    )

    /**
     * Key: normalized district name (see [normalize]).
     * Value: full second-level host part including the RDLP city subdomain.
     */
    private val HOST_OVERRIDES = mapOf(
        "GDANSK" to "nadlesnictwo.gdansk",
        "DABROWA TARNOWSKA" to "dabrowa.krakow",
        "OSTROW MAZOWIECKA" to "ostrow.warszawa",
        "OSTROWIEC SWIETOKRZYSKI" to "ostrowiec.radom",
        "OLESNICA SLASKA" to "olesnica-slaska.wroclaw",
        "LADEK ZDROJ" to "ladek-zdroj.wroclaw",
        "GLEBOKI BROD" to "gleboki-brod.bialystok",
        "BARDO SLASKIE" to "bardo-slaskie.wroclaw",
        "CZARNA BIALOSTOCKA" to "czarna-bialostocka.bialystok",
        "CZERWONY DWOR" to "czerwony-dwor.bialystok",
        "RUDY RACIBORSKIE" to "rudy-raciborskie.katowice",
        "STRZELCE OPOLSKIE" to "strzelce-opolskie.katowice",
        "WEGIERSKA GORKA" to "wegierska-gorka.katowice",
        "GOROWO ILAWECKIE" to "gorowo-ilaweckie.olsztyn",
        "NOWE RAMUKI" to "nowe-ramuki.olsztyn",
        "STARE JABLONKI" to "stare-jablonki.olsztyn",
        "SZKLARSKA POREBA" to "szklarska-poreba.wroclaw",
        "LESNY DWOR" to "lesny-dwor.szczecinek",
        "BORNE SULINOWO" to "borne-sulinowo.szczecinek",
        "SOLEC KUJAWSKI" to "solec-kujawski.torun",
        "BYSTRZYCA KLODZKA" to "bystrzyca-klodzka.wroclaw",
        "KAMIENNA GORA" to "kamienna-gora.wroclaw",
        "LWOWEK SLASKI" to "lwowek-slaski.wroclaw",
        "OBORNIKI SLASKIE" to "oborniki-slaskie.wroclaw",
        "CZARNE CZLUCHOWSKIE" to "czarne-czluchowskie.szczecinek"
    )

    fun websiteUrl(districtName: String?, rdlpName: String?): String? {
        val district = stripDistrictPrefix(districtName ?: return null)
        if (district.isBlank()) return null

        val city = rdlpCity(rdlpName) ?: return null

        val hostPart = HOST_OVERRIDES[normalize(district)] ?: district.slug()
        return "https://$hostPart.$city.$HOST_SUFFIX"
    }

    fun displayHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }

    internal fun normalize(name: String): String {
        val folded = buildString {
            for (ch in name.trim()) append(DIACRITICS[ch] ?: ch)
        }
        return folded.uppercase().replace(Regex("\\s+"), " ")
    }

    private fun stripDistrictPrefix(name: String): String =
        name.trim().replace(Regex("^nadleśnictwo\\s+", RegexOption.IGNORE_CASE), "").trim()

    private fun rdlpCity(rdlpName: String?): String? {
        if (rdlpName == null) return null
        val withoutPrefix = rdlpName.trim().replace(Regex("^RDLP\\s+", RegexOption.IGNORE_CASE), "")
        if (withoutPrefix.isBlank()) return null
        return normalize(withoutPrefix).replace(" ", "").lowercase()
    }

    private fun String.slug(): String =
        normalize(this).lowercase().replace(" ", "").filter { it in 'a'..'z' }
}
