package com.indiana.zwl.shared.update

data class ReleaseNote(
    val version: String,
    val items: List<String>
)

object WhatsNew {

    private val notes = listOf(
        ReleaseNote(
            version = "1.7",
            items = listOf(
                "Okienko „Co nowego” — po każdej aktualizacji krótka lista najważniejszych zmian, żeby niczego nie przegapić.",
                "Pobliskie punkty — w widoku punktu (własnego, POI albo źródła wody) zobaczysz listę innych punktów w okolicy z odległością; ile kilometrów to „blisko”, ustawisz suwakiem w Ustawieniach."
            )
        ),
        ReleaseNote(
            version = "1.6",
            items = listOf(
                "Szukaj strefy nadleśnictwa — nowy przycisk na mapie: wpisz nazwę (polskie znaki nie muszą być poprawne), posortuj wyniki po nazwie lub odległości, a mapa przeleci na wybraną strefę.",
                "Mapa wskakuje na wklejone współrzędne — „Otwórz punkt ze współrzędnych” od razu centruje mapę w podanym miejscu.",
                "Niezawodne tapnięcia w strefy — poprawiony błąd, gdy dotknięcie strefy czasem nie reagowało, a ikony punktów znikały pod wypełnieniem.",
                "„Nie wygaszaj ekranu” — nowe ustawienie trzyma ekran włączony, gdy patrzysz na mapę; panel „Wyświetlanie na mapie” zmienił nazwę na „Ustawienia”.",
                "Wygodniejsze punkty — zbiorcze usuwanie z trybem „Zaznacz”, zagrożenie pożarowe i dane BDL widoczne już przed zapisaniem, a zapisane punkty mają datę i sortowanie."
            )
        )
    )

    fun latestNote(): ReleaseNote? {
        var newest: ReleaseNote? = null
        for (note in notes) {
            val current = newest
            if (current == null || UpdateTools.isUpdateAvailable(current.version, note.version)) {
                newest = note
            }
        }
        return newest
    }

    /**
     * The note for [currentVersion]: the exact match, or the newest note that
     * is not newer than the installed version (a user who skipped releases
     * still sees the latest changes). Null when no note applies.
     */
    fun noteFor(currentVersion: String): ReleaseNote? {
        var newest: ReleaseNote? = null
        for (note in notes) {
            if (UpdateTools.isUpdateAvailable(currentVersion, note.version)) continue
            val current = newest
            if (current == null || UpdateTools.isUpdateAvailable(current.version, note.version)) {
                newest = note
            }
        }
        return newest
    }

    /**
     * Decides whether the "what's new" popup should show.
     * Release: only when the applicable note is newer than the last seen
     * version (so it shows once per version, on both upgrade and fresh
     * install, and never again after being seen). Debug: always the latest
     * note, with the caller skipping the seen-marker write so the popup
     * returns on every launch.
     */
    fun noteToShow(currentVersion: String, lastSeenVersion: String?, debugBuild: Boolean): ReleaseNote? {
        if (debugBuild) return latestNote()
        val note = noteFor(currentVersion) ?: return null
        val seen = lastSeenVersion ?: return note
        return if (UpdateTools.isUpdateAvailable(seen, note.version)) note else null
    }
}
