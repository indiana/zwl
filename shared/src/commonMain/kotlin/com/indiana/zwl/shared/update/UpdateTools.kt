package com.indiana.zwl.shared.update

object UpdateTools {
    fun isUpdateAvailable(installed: String, store: String): Boolean {
        val current = parse(installed)
        val latest = parse(store)
        val size = maxOf(current.size, latest.size)
        for (i in 0 until size) {
            val a = current.getOrElse(i) { 0 }
            val b = latest.getOrElse(i) { 0 }
            if (a != b) return a < b
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        val cleaned = version.trim().trimStart('v', 'V', '=').takeWhile { it.isDigit() || it == '.' }
        return cleaned.split('.').map { part -> part.ifEmpty { "0" }.toIntOrNull() ?: 0 }
    }
}
