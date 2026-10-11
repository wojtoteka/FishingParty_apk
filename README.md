# Fishing Party

Zręcznościówka na Androida: steruj łódką, łów ryby, otwieraj bramy kolejnych etapów i dopłyń do mety, omijając piranie, ośmiornice i rekiny.

## Zawartość

- **1000 poziomów** w 40 światach (10 palet kolorów). Każdy poziom ma od 1 do 7 etapów – brama otwiera się po złowieniu wymaganej liczby ryb. Poziomy 501–999 to druga, trudniejsza wyprawa (pierwsze 500 jest bez zmian).
- **Poziom 1000 – Wielki Finał**: ręcznie ułożony, 7 etapów w barwach 7 różnych światów, każdy z innym zestawem potworów, port na mecie w osobnej złotej palecie (`legend`). Na mapie ma własny port z dużą wyspą za ostatnimi wrotami, a za pierwsze ukończenie – bonus 5000 monet i odznakę „Żywa legenda”.
- Poziomy są generowane deterministycznie z numeru poziomu (`LevelGenerator`), więc każdy gracz dostaje identyczną planszę – to podstawa uczciwego rankingu.
- 9 typów poziomów (skalista rafa, archipelag, kanał, prąd, gorączka złota, wyścig z czasem, ławica piranii, starcie z bestią…) plus Wielki Finał, 7 rodzajów przeciwników, złote i niebieskie ryby, combo do x5.
- 3 gwiazdki za poziom (meta + dwa zmienne cele), mapa przygody, sklep (łodzie, żagle, załoga, wzmocnienia), 43 osiągnięcia, nagroda dnia.
- **Ranking online top 100 i konto** (od wersji 2.1, serwer `Top_FishingParty` na https://fp.wojtoteka.ovh). Punkty rankingowe = suma najlepszych wyników z każdego poziomu + trofea za passę.
  - **Tryb rankingowy (nick + hasło)** – akceptacja polityki prywatności i regulaminu, nick i hasło. Nowy nick zakłada konto, istniejący z poprawnym hasłem loguje i przywraca postęp (np. po reinstalacji). Hasła nie da się zresetować.
  - **Konto e-mail** – e-mail + nick + hasło, potwierdzane kodem z maila (SMTP); pozwala ustawić nowe hasło kodem. Do konta „nick + hasło” można później dodać e-mail.
  - Synchronizacja na bieżąco: ukończony poziom leci na serwer od razu, pełny zapis gry kilka sekund po każdej zmianie i przy wyjściu z aplikacji. Na nowym telefonie zapis jest przywracany; przy konflikcie dwóch urządzeń gra pyta, który postęp zachować.
  - Bez internetu gra działa normalnie, a ekrany KONTO i RANKING proszą o połączenie. Niewysłane wyniki czekają i lecą same po powrocie sieci.
  - Konto można usunąć w grze (KONTO → USUŃ KONTO) – serwer kasuje wszystkie dane.

## Ochrona wyników

- Zapis szyfrowany AES-256-GCM kluczem z Android Keystore (`SaveCrypto`, `GameRepository`) – zmiana pliku lub przeniesienie go na inne urządzenie jest wykrywane.
- Najważniejsze liczby (monety, rekordy, wynik w trakcie gry) trzymane w pamięci w postaci zamaskowanej (`GuardedLong`).
- Każdy wynik przechodzi przez `ScoreValidator`: limit maksymalnego wyniku liczony z definicji poziomu, minimalny możliwy czas, liczby ryb. Ten sam kod może później działać na serwerze rankingu (razem z Play Integrity API).
- Ostatnie zweryfikowane przejazdy (`ScoreRecord`) są wysyłane na serwer rankingu (`online/OnlineManager`). Serwer sprawdza każdy przejazd na tych samych limitach poziomów co gra (`data/level-limits.json` eksportowany przez `LevelLimitsExportTest`), pilnuje kolejności poziomów, czasu i nakładania się przejazdów, przyjmuje sygnały z gry (wykryte zmiany zapisu, edycja pamięci) i ukrywa podejrzane konta z rankingu.
- Każde żądanie do serwera jest podpisane HMAC-SHA256 (czas + jednorazowy nonce), sesja konta leży w osobnym zaszyfrowanym pliku (`AccountStore`), nie w zapisie gry.

## Grafika i dźwięk

- Tylko oryginalne sprity. Warianty kolorystyczne (palety światów, złote ryby, skórki łodzi…) robi skrypt `tools/recolor_sprites.py` z `tools/original_sprites/` – w grze nic nie jest generowane.
- Muzyka i efekty: port syntezatora `audio.beep` z microStudio (`audio/Beeper.kt`) z oryginalnymi sekwencjami nut.
- Font: BitCell (memesbruh03).

## Budowanie

- Gradle 9.8.1 uruchamiany na **JDK 27** (`gradle/gradle-daemon-jvm.properties`), kompilacja przez toolchain JDK 27.
- AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36, minSdk 26.
- Bajtkod ma target 26 – stabilny Kotlin nie obsługuje jeszcze celu 27 (pojawi się w Kotlinie 2.5).

Ranking online wymaga w `local.properties` (plik poza repozytorium) sekretu podpisu – tego samego co `HMAC_SECRET` w `.env` serwera:

```
fp.hmacSecret=...
fp.apiUrl=https://fp.wojtoteka.ovh/api/v1   # opcjonalnie, to wartość domyślna
```

Bez `fp.hmacSecret` gra działa, a ekrany konta i rankingu pokazują, że ranking jest niedostępny. Po zmianie generatora poziomów trzeba odświeżyć limity na serwerze: `./gradlew testDebugUnitTest --tests "*LevelLimitsExportTest*"` i skopiować `app/build/level-limits.json` do `Top_FishingParty/data/`.

```
./gradlew assembleDebug        # APK testowe
./gradlew testDebugUnitTest    # testy JVM (generator, symulacja z botem, zapis, szyfrowanie, podpis API)
./gradlew bundleRelease        # AAB do Google Play (wymaga konfiguracji podpisu)
```
