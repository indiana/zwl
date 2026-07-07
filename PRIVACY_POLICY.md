# Polityka Prywatności aplikacji „Legalny Bushcraft”

**Ostatnia aktualizacja:** 7 lipca 2026 r.

Niniejsza Polityka Prywatności określa zasady przetwarzania i ochrony danych osobowych oraz informacji o lokalizacji użytkowników aplikacji mobilnej **„Legalny Bushcraft”** (identyfikator pakietu: **com.indiana.zwl**, zwanej dalej „Aplikacją”), której administratorem i deweloperem jest **Piotr Jakubowski** (zwany dalej „Administratorem”).

## 1. Zbieranie i przetwarzanie danych osobowych
Aplikacja została zaprojektowana z myślą o maksymalnej ochronie prywatności użytkowników. Działa w modelu *Offline-First* i **nie gromadzi, nie przechowuje na żadnych zewnętrznych serwerach, ani nie udostępnia** żadnych danych osobowych użytkowników podmiotom trzecim.

## 2. Dane o lokalizacji (GPS)
Aplikacja wymaga dostępu do lokalizacji urządzenia (uprawnienia `ACCESS_FINE_LOCATION` oraz `ACCESS_COARSE_LOCATION`), aby realizować swoje główne funkcje użytkowe:
- Weryfikacja na mapie, czy użytkownik znajduje się w wyznaczonej strefie programu „Zanocuj w lesie”.
- Kalkulacja odległości oraz wskazywanie kierunku (kompas) do najbliższej legalnej strefy biwakowej.

**Ważne:** Dane o lokalizacji są przetwarzane **wyłącznie w czasie rzeczywistym i lokalnie na urządzeniu użytkownika**. Aplikacja nie zbiera danych o lokalizacji w tle, nie rejestruje historii lokalizacji ani nie przesyła współrzędnych geograficznych na serwery zewnętrzne.

## 3. Komunikacja z usługami zewnętrznymi
Aplikacja łączy się z internetem wyłącznie w celach technicznych i informacyjnych:
- Pobieranie i aktualizacja granic stref leśnych z serwerów Banku Danych o Lasach (BDL) pod adresem: `https://mapserver.bdl.lasy.gov.pl/`.
- Sprawdzanie stopnia zagrożenia pożarowego dla współrzędnych geograficznych użytkownika za pośrednictwem zapytań API do publicznych baz danych.

Zapytania te są anonimowe, nie są powiązane z żadnym profilem użytkownika i nie służą do identyfikacji osób fizycznych.

## 4. Uprawnienia urządzenia i czujniki
Aplikacja korzysta z następujących uprawnień oraz podzespołów urządzenia:
- **Lokalizacja (dokładna i przybliżona):** Niezbędna do ustalenia strefy i nawigacji.
- **Czujniki urządzenia (akcelerometr, magnetometr, żyroskop):** Wykorzystywane wyłącznie lokalnie do prawidłowego działania kompasu oraz optymalizacji zużycia baterii.

## 5. Przechowywanie i usuwanie danych (Data Retention & Deletion)
Ponieważ Aplikacja **nie gromadzi ani nie przechowuje żadnych danych osobowych** na zewnętrznych serwerach ani w chmurze, Administrator nie dysponuje żadnymi danymi użytkowników, które mogłyby podlegać usunięciu. Usunięcie Aplikacji z urządzenia powoduje bezpowrotne skasowanie wszelkich lokalnych danych tymczasowych (takich jak pobrany cache map).

## 6. Kontakt i prawa użytkownika (RODO)
Użytkownikom przysługuje prawo do wglądu w funkcjonowanie aplikacji oraz zgłaszania pytań dotyczących prywatności. W przypadku pytań dotyczących niniejszej Polityki Prywatności, prosimy o kontakt:
- Poprzez zgłoszenie w sekcji *Issues* w serwisie GitHub na stronie projektu.
