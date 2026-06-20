# Reguły Projektu

## Obsługa Artefaktów Buildów
- Nigdy nie pobieraj skompilowanych plików binarnych (takich jak `.apk` czy `.aab`) na lokalny dysk komputera ani do katalogu roboczego agenta.
- Użytkownik pobiera te pliki samodzielnie bezpośrednio z interfejsu GitHub Actions.
- Po wrzuceniu zmian na GitHuba zawsze sprawdzaj powodzenie builda (używając `assets/gh.exe run view`). Jeśli kompilacja zakończy się sukcesem, poinformuj użytkownika o gotowych artefaktach.
- W przypadku niepowodzenia builda z powodu błędów kompilacji, natychmiast użyj GitHuba do pobrania logów błędu z artefaktów workflow (np. `assets/gh.exe run download -n build-log`), przeanalizuj ten log i podejmij próbę samodzielnego naprawienia kodu.

## Środowisko i Narzędzia
- Kliencka linia poleceń GitHuba (`gh.exe`) znajduje się w katalogu `assets/` projektu (tzn. `D:\priv\zwl\assets\gh.exe`). Używaj tej ścieżki zamiast globalnego polecenia `gh`.
