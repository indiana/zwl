# Dokument Wymagań Produktowych (PRD)

**Projekt:** Aplikacja Android „Zanocuj w Lesie – Lokator”  
**Wersja:** 1.1 (Czerwiec 2026)  
**Status:** Gotowy do implementacji  

---

## 1. Cel projektu i grupy docelowe
Celem aplikacji jest natychmiastowe udzielenie użytkownikowi odpowiedzi na pytanie, czy jego aktualna pozycja GPS znajduje się wewnątrz oficjalnej strefy programu „Zanocuj w lesie” (ZwL) oraz jakie zasady bezpieczeństwa pożarowego obowiązują w tym miejscu. W przypadku znajdowania się poza strefą, aplikacja działa jako nawigator, wskazując precyzyjną odległość oraz kierunek do najbliższego obszaru legalnego biwakowania.

Targetem są biwakowicze, miłośnicy bushcraftu i turyści, którzy potrzebują niezawodnego narzędzia działającego w warunkach braku zasięgu sieci komórkowych.

---

## 2. Architektura i Przepływ Synchronizacji (Startup Flow)
Aplikacja projektowana jest w architekturze *Offline-First*. Obliczenia przestrzenne nie mogą polegać na stałym dostępie do internetu.

1. **Weryfikacja bazy lokalnej (Cache):** Przy starcie aplikacja sprawdza obecność zapisanych wcześniej poligonów stref ZwL w lokalnej bazie (np. SQLite/Room).
2. **Sprawdzenie łączności z siecią:** System podejmuje próbę kontaktu z serwerem Banku Danych o Lasach (BDL).
3. **Scenariusz A (Online):** Jeśli sieć jest dostępna, aplikacja weryfikuje aktualność danych. W razie potrzeby pobiera nową paczkę poligonów i aktualizuje dzienny stopień zagrożenia pożarowego.
4. **Scenariusz B (Offline - Fallback):** W przypadku braku sieci:
   - Jeśli baza lokalna zawiera dane: Aplikacja uruchamia się w trybie lokalnym, wyświetlając ostrzeżenie: *„Tryb offline. Dane stref mogą nie być w pełni aktualne”*.
   - Jeśli baza lokalna jest pusta (pierwsze uruchomienie): Aplikacja wyświetla błąd: *„Brak dostępu do internetu i brak zapisanych lokalnie danych o strefach Zanocuj w Lesie”* i blokuje dalsze działanie do czasu połączenia z siecią.

---

## 3. Szczegółowe Wymagania Funkcjonalne (FR)

### FR-1: Ekran Główny – Scenariusz „W STREFIE” (Status 2.1)
* **Wskaźnik lokalizacji:** Zielona, jednoznaczna oprawa wizualna interfejsu.
* **Główny komunikat:** Duży napis: **„Jesteś w strefie Zanocuj w Lesie”**.
* **Nazwa Nadleśnictwa:** Wyświetlenie nazwy nadleśnictwa (pobranej z metadanych poligonu, w którym znajduje się punkt GPS).
* **Zagrożenie pożarowe:** Informacja o aktualnym stopniu (od 0 do 3). W trybie offline: *„Status zagrożenia pożarowego: Nieznany (brak sieci)”*.
* **Status kuchenek gazowych:**
  - Stopień zagrożenia 0, 1, 2: **„Używanie kuchenek gazowych: DOZWOLONE”** (kolor zielony/neutralny).
  - Stopień zagrożenia 3: **„Używanie kuchenek gazowych: BEZWZGLĘDNY ZAKAZ”** (kolor czerwony, pulsujący).
  - Brak danych (offline): **„Używanie kuchenek dozwolone warunkowo (brak aktualnych danych pożarowych)”** (kolor żółty).

### FR-2: Ekran Główny – Scenariusz „POZA STREFĄ” (Status 2.2)
* **Wskaźnik lokalizacji:** Ostrzegawcza, żółto-pomarańczowa oprawa wizualna interfejsu.
* **Główny komunikat:** Wyraźny napis: **„Jesteś poza strefą Zanocuj w Lesie”**.
* **Informacje o najbliższej strefie:**
  - **Nazwa najbliższej strefy:** `<Nazwa nadleśnictwa najbliższej strefy>` (np. *Nadleśnictwo Spychowo*).
  - **Odległość do strefy:** Wyświetlana w kilometrach z dokładnością do jednego miejsca po przecinku (np. `Odległość: 1.4 km`). Jeśli odległość wynosi poniżej 100 metrów, jednostka automatycznie przełącza się na metry (np. `Odległość: 85 m`). Dystans liczony jest jako najkrótsza linia prosta od punktu GPS użytkownika do najbliższej krawędzi (wierzchołka) poligonu strefy.
  - **Kierunek do strefy:** Wyświetlanie tekstowe kierunku świata (np. `Kierunek: Północny-Wschód (NE)`) połączone z dynamiczną strzałką kompasu.
* **Interakcja kompasu:** Strzałka kierunkowa musi przetwarzać dane z magnetometru i akcelerometru urządzenia w czasie rzeczywistym. Obrót użytkownika w miejscu musi powodować adekwatny obrót strzałki, wskazując fizyczne położenie granicy strefy w terenie.

### FR-3: Widok Mapy (Status 3.0)
* **Przełącznik widoków:** Intuicyjny przycisk na dole ekranu lub zakładka pozwalająca na zmianę widoku tekstowego na mapę i odwrotnie.
* **Wizualizacja pozycji:** Wyświetlanie aktualnej pozycji użytkownika jako niebieskiej, pulsującej kropki.
* **Wizualizacja stref:** Rysowanie granic obszarów ZwL jako półprzezroczystych poligonów (np. zielone wypełnienie z przezroczystością 30% i ciemnozieloną, wyraźną krawędzią).
* **Obsługa Offline:** Silnik mapowy musi wspierać renderowanie wyrenderowanych wcześniej kafelków (np. format `.mbtiles` lub `.map` w Osmdroid / Mapsforge), aby mapa podkładowa działała bez dostępu do sieci komórkowej.

---

## 4. Wymagania Niefunkcjonalne (NFR)

| Identyfikator | Kategoria | Opis wymagania |
| :--- | :--- | :--- |
| **NFR-1** | **Działanie Offline** | Sprawdzanie przynależności punktu do wielokąta (Point-in-Polygon) oraz kalkulacja odległości i azymutu do najbliższego poligonu musi odbywać się lokalnie na urządzeniu. |
| **NFR-2** | **Wydajność** | Czas wykonania zapytania przestrzennego i znalezienia najbliższego poligonu po odebraniu nowego punktu GPS nie może przekraczać 500 ms. |
| **NFR-3** | **Zarządzanie energią** | Aplikacja musi optymalizować częstotliwość odpytywania odbiornika GPS za pomocą `FusedLocationProviderClient`. Gdy użytkownik nie zmienia pozycji (wykryte przez akcelerometr), interwał próbkowania GPS powinien zostać wydłużony do 30 sekund w celu oszczędzania baterii. |
| **NFR-4** | **Uprawnienia** | Wymagane jawne zatwierdzenie uprawnień `ACCESS_FINE_LOCATION`. Aplikacja musi obsłużyć sytuację odmowy przyznania uprawnień dedykowanym ekranem informacyjnym. |

---

## 5. Źródła Danych i Integracja (Dane Techniczne)
1. **Warstwa Geograficzna ZwL:** Pobranie danych przestrzennych z serwera ArcGIS REST Lasów Państwowych (Bank Danych o Lasach): `WFS_BDL_mapa_turystyczna`, ID warstwy: `76` (Obszar programu Zanocuj w lesie). Eksport do lokalnego pliku GeoJSON lub bazy danych SQLite z rozszerzeniem Spatialite przed dystrybucją aplikacji.
2. **API Zagrożenia Pożarowego:** Endpoint BDL: `WMS_zagrozenie_pozarowe_w_lasach` – odpytywany w trybie online w celu pobrania tekstowego i numerycznego statusu zagrożenia dla współrzędnych użytkownika.
