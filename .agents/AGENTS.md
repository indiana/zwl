# Reguły Projektu

## Obsługa Artefaktów Buildów
- Nigdy nie pobieraj skompilowanych plików binarnych (takich jak `.apk` czy `.aab`) na lokalny dysk komputera ani do katalogu roboczego agenta.
- Użytkownik pobiera te pliki samodzielnie bezpośrednio z interfejsu GitHub Actions.
- Po zakończeniu builda na GitHub Actions, jedynie zweryfikuj sukces kompilacji (np. za pomocą `gh run view`) i poinformuj użytkownika o powodzeniu, podając informację o dostępnych artefaktach na GitHubie.
