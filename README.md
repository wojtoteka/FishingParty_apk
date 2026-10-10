# JUŻ NIEDŁUGO NOWA AKTUALIZACJA
## Fishing Party

Zręcznościówka na Androida: steruj łódką, łów ryby, otwieraj bramy kolejnych etapów i dopłyń do mety, omijając piranie, ośmiornice i rekiny.

## Zawartość

- **1000 poziomów** w 40 światach (10 palet kolorów). Każdy poziom ma od 1 do 7 etapów – brama otwiera się po złowieniu wymaganej liczby ryb. Poziomy 501–999 to druga, trudniejsza wyprawa (pierwsze 500 jest bez zmian).
- **Poziom 1000 – Wielki Finał**: ręcznie ułożony, 7 etapów w barwach 7 różnych światów, każdy z innym zestawem potworów, port na mecie w osobnej złotej palecie (`legend`). Na mapie ma własny port z dużą wyspą za ostatnimi wrotami, a za pierwsze ukończenie – bonus 5000 monet i odznakę „Żywa legenda”.
- Poziomy są generowane deterministycznie z numeru poziomu (`LevelGenerator`), więc każdy gracz dostaje identyczną planszę – to podstawa uczciwego rankingu.
- 9 typów poziomów (skalista rafa, archipelag, kanał, prąd, gorączka złota, wyścig z czasem, ławica piranii, starcie z bestią…) plus Wielki Finał, 7 rodzajów przeciwników, złote i niebieskie ryby, combo do x5.
- 3 gwiazdki za poziom (meta + dwa zmienne cele), mapa przygody, sklep (łodzie, żagle, załoga, wzmocnienia), 43 osiągnięcia, nagroda dnia.
- Ranking: ekran „SOON”. Punkty rankingowe = suma najlepszych wyników z każdego poziomu.

## Ochrona wyników

- Zapis szyfrowany AES-256-GCM kluczem z Android Keystore (`SaveCrypto`, `GameRepository`) – zmiana pliku lub przeniesienie go na inne urządzenie jest wykrywane.
- Najważniejsze liczby (monety, rekordy, wynik w trakcie gry) trzymane w pamięci w postaci zamaskowanej (`GuardedLong`).
- Każdy wynik przechodzi przez `ScoreValidator`: limit maksymalnego wyniku liczony z definicji poziomu, minimalny możliwy czas, liczby ryb. Ten sam kod może później działać na serwerze rankingu (razem z Play Integrity API).
- Ostatnie zweryfikowane przejazdy są kolejkowane w zapisie (`ScoreRecord`) pod przyszłe wysyłanie do rankingu.

## Grafika i dźwięk

- Tylko oryginalne sprity. Warianty kolorystyczne (palety światów, złote ryby, skórki łodzi…) robi skrypt `tools/recolor_sprites.py` z `tools/original_sprites/` – w grze nic nie jest generowane.
- Muzyka i efekty: port syntezatora `audio.beep` z microStudio (`audio/Beeper.kt`) z oryginalnymi sekwencjami nut.
- Font: BitCell (memesbruh03).

## Budowanie

- Gradle 9.8.1 uruchamiany na **JDK 27** (`gradle/gradle-daemon-jvm.properties`), kompilacja przez toolchain JDK 27.
- AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36, minSdk 26.
- Bajtkod ma target 26 – stabilny Kotlin nie obsługuje jeszcze celu 27 (pojawi się w Kotlinie 2.5).

```
./gradlew assembleDebug        # APK testowe
./gradlew testDebugUnitTest    # testy JVM (generator, symulacja z botem, zapis, szyfrowanie)
./gradlew bundleRelease        # AAB do Google Play (wymaga konfiguracji podpisu)
```
