package com.indiana.zwl.shared.offline

/**
 * Polish display helpers for downloaded offline areas. Pure-Kotlin civil-date
 * math (no kotlinx-datetime dependency) — call sites pass the device's UTC
 * offset so "dziś"/"wczoraj" follow the user's local calendar, not UTC.
 */
object OfflineAreaNames {

    /** Auto display name, e.g. "Obszar 03.09 14:32". */
    fun autoName(downloadedAtMillis: Long, offsetMinutes: Int): String {
        val (_, month, day) = civilFromDays(localDay(downloadedAtMillis, offsetMinutes))
        val minuteOfDay = minuteOfDay(downloadedAtMillis, offsetMinutes)
        return "Obszar ${pad2(day)}.${pad2(month)} ${pad2(minuteOfDay / 60)}:${pad2(minuteOfDay % 60)}"
    }

    /**
     * Human "age" of a download: "dziś, 14:32" / "wczoraj" / "3 dni temu" /
     * absolute date for anything older. Future timestamps (clock skew) render
     * as "dziś".
     */
    fun ageLabel(downloadedAtMillis: Long, nowMillis: Long, offsetMinutes: Int): String {
        val day = localDay(downloadedAtMillis, offsetMinutes)
        val nowDay = localDay(nowMillis, offsetMinutes)
        val diff = nowDay - day
        return when {
            diff <= 0 -> "dziś, ${pad2(minuteOfDay(downloadedAtMillis, offsetMinutes) / 60)}:" +
                pad2(minuteOfDay(downloadedAtMillis, offsetMinutes) % 60)
            diff == 1L -> "wczoraj"
            diff in 2..6 -> "$diff dni temu"
            else -> {
                val (year, month, dayOfMonth) = civilFromDays(day)
                "${pad2(dayOfMonth)}.${pad2(month)}.$year"
            }
        }
    }

    private fun localDay(millis: Long, offsetMinutes: Int): Long =
        floorDiv(millis + offsetMinutes * 60_000L, 86_400_000L)

    private fun minuteOfDay(millis: Long, offsetMinutes: Int): Int {
        val localSeconds = floorDiv(millis + offsetMinutes * 60_000L, 1000L)
        return (localSeconds % 86_400L).toInt() / 60
    }

    private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

    private fun floorDiv(a: Long, b: Long): Long = Math.floorDiv(a, b)

    /** Days-since-epoch -> (year, month, day); Howard Hinnant's civil algorithm. */
    private fun civilFromDays(z: Long): Triple<Long, Int, Int> {
        val shifted = z + 719_468
        val era = (if (shifted >= 0) shifted else shifted - 146_096) / 146_097
        val dayOfEra = shifted - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        val year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val mp = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        return Triple(if (month <= 2) year + 1 else year, month.toInt(), day.toInt())
    }
}
