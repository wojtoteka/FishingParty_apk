# Fishing Party

Zręcznościówka na Androida: steruj łódką, łów ryby, otwieraj bramy kolejnych etapów i dopłyń do mety, omijając potwory.

## W grze

- **Kampania:** 1000 poziomów w 40 światach, na końcu Wielki Finał. Dalej Bezkresne Morze, do poziomu 20 000.
- **Wyzwania:** poziom dnia, poziom tygodnia, turnieje weekendowe.
- **Ranking online:** ogólny i sezonowy, konto z nickiem, hasłem albo e-mailem, synchronizacja postępu między telefonami.
- **Kreator poziomów:** publikowanie, gwiazdki od graczy, udostępnianie linkiem.
- **Sklep i targ:** łodzie, żagle, załoga, wzmocnienia, własne kolory i wzory, sprzedaż projektów innym graczom.
- **Sterowanie:** palec, joystick albo klawiatura. Bez internetu gra działa normalnie, a wyniki czekają na wysłanie.

Plansze powstają deterministycznie z numeru poziomu albo z seeda, więc każdy gracz dostaje identyczny poziom.

## Budowanie

Wymaga JDK 27. Stos: Kotlin, Jetpack Compose, minSdk 26, targetSdk 36. Dokładne wersje są w `gradle/libs.versions.toml`.

```
./gradlew assembleDebug        # APK testowe
./gradlew testDebugUnitTest    # testy JVM
./gradlew bundleRelease        # AAB do Google Play (wymaga konfiguracji podpisu)
```

Ranking i konta wymagają w `local.properties` (poza repozytorium) sekretu podpisu żądań:

```
fp.hmacSecret=...
```

Bez niego gra działa, a ekrany konta i rankingu pokazują, że ranking jest niedostępny. Opcjonalnie można ustawić `fp.apiUrl`, `fp.linkHost` i `fp.betaApiUrl`.

Wersja debug łączy się z betą serwera (osobne konta i ranking), a release z serwerem gry.

## Ochrona wyników

- Zapis gry jest szyfrowany kluczem z Android Keystore, a najważniejsze liczby są maskowane w pamięci.
- Każdy wynik przechodzi lokalną walidację (`ScoreValidator`), a o wejściu do rankingu decyduje serwer.
- Żądania są podpisane, a telefon potwierdza się kluczem sprzętowym, więc przerobiona aplikacja nie przejdzie weryfikacji.

## Grafika i dźwięk

- Tylko oryginalne sprite'y. Warianty kolorystyczne tworzy `tools/recolor_sprites.py` z `tools/original_sprites/`.
- Muzyka i efekty: port syntezatora `audio.beep` z microStudio (`audio/Beeper.kt`).
- Font: BitCell (memesbruh03).
