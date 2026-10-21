# Fishing Party

Zręcznościówka na Androida: steruj łódką, łów ryby, otwieraj bramy kolejnych etapów i dopłyń do mety, omijając piranie, ośmiornice, rekiny i widmokudy.

## Zawartość

- **1000 poziomów** w 40 światach (10 palet kolorów). Każdy poziom ma od 1 do 7 etapów – brama otwiera się po złowieniu wymaganej liczby ryb. Poziomy 501–999 to druga, trudniejsza wyprawa (pierwsze 500 jest bez zmian).
- **Poziom 1000 – Wielki Finał**: ręcznie ułożony, 7 etapów w barwach 7 różnych światów, każdy z innym zestawem potworów, port na mecie w osobnej złotej palecie (`legend`). Na mapie ma własny port z dużą wyspą za ostatnimi wrotami, a za pierwsze ukończenie – bonus 5000 monet i odznakę „Żywa legenda”.
- **Bezkresne Morze – poziomy 1001+** (od wersji 2.2): po Wielkim Finale wrota portu się otwierają, a mapa sama dobudowuje kolejne światy (losowa paleta i nazwa, boss co 25 poziomów). Każdy poziom powstaje z własnego **seeda** (`LevelSeed`, tylko wewnątrz gry – nie jest pokazywany w interfejsie) według „reguł świata” (`SeedProfile`), które pilnują grywalności i trudności – wyraźnie trudniej niż na początku gry, lżej niż Wielki Finał. Plansze się nie powtarzają, za ryby są monety i punkty do rankingu. Limit to poziom 20 000.
- Poziomy są generowane deterministycznie z numeru poziomu (`LevelGenerator`), a od 1001 z seeda (`SeedLevels`), więc każdy gracz dostaje identyczną planszę – to podstawa uczciwego rankingu. Liczby decydujące o maksymalnym wyniku generator z seeda liczy wyłącznie na liczbach całkowitych, żeby serwer rankingu mógł je powtórzyć 1:1.
- **Poziom dnia i poziom tygodnia** (od wersji 2.3): kolorowe przyciski w menu. Seed planszy podaje serwer, więc wszyscy gracze dostają ten sam poziom; dzienny zmienia się o 00:00, tygodniowy w poniedziałek o 00:00 (czas polski). Plansze powstają z seeda tym samym generatorem co Bezkresne Morze (`Challenges`, `ChallengeKind` z własnymi „regułami świata”) – tydzień jest wyraźnie trudniejszy. Każdy okres zalicza się raz: punkty rankingowe = wynik (dzień) albo wynik × 2 (tydzień) plus premia w monetach. Po ukończeniu przycisk i okno odliczają czas do nowego poziomu. Pobrany seed działa bez internetu do końca okresu, a wynik z telefonu offline czeka w kolejce. Gdy gra ma zgodę na powiadomienia, po resecie przypomina o nowym poziomie (codziennie o wybranej godzinie 9–13, w poniedziałek o poziomie tygodnia) – najwyżej raz dziennie, tylko gdy gra nie była jeszcze otwarta i poziom nie jest ukończony, zawsze kilka godzin przed przypomnieniami o passie (USTAWIENIA → POWIADOMIENIA, domyślnie włączone).
- 9 typów poziomów (skalista rafa, archipelag, kanał, prąd, gorączka złota, wyścig z czasem, ławica piranii, starcie z bestią…) plus Wielki Finał, 8 rodzajów przeciwników, złote i niebieskie ryby, combo do x5.
- **Widmokuda** (od wersji 2.3, od poziomu 20) – najgroźniejszy potwór, fioletowa drapieżna ryba z kłami i półprzezroczystym ogonem (narysowana od zera w `tools/recolor_sprites.py`): patroluje i patrzy przed siebie, a gdy zobaczy łódź (w zasięgu wzroku i bez skały, wyspy ani brzegu po drodze – przez przeszkody nie widzi), oko i kolce na grzbiecie robią się czerwone, nad głową miga „!” i po ok. pół sekundy rzuca się błyskawicznie w miejsce łodzi; po ataku (trafionym czy nie) odpływa od łodzi i przez kilka sekund pływa spokojnie jak ryba, zanim znów zacznie polować. Zamiast dokładać potwory zastępuje słabsze (etap z kilkoma rekinami ma dwa rekiny i widmokudę), a pozycje ryb, skał i pozostałych potworów się nie zmieniają (`Stalkers`). Czas wzorcowy i limity wyniku liczone są jak wcześniej, więc serwer rankingu jej nie zna. Każdy nowy potwór jest przedstawiany na karcie poziomu („Nowy potwór!”) przez 4 poziomy od pierwszego pojawienia się.
- **Piasek przy brzegu** – łódź wpływa na niego tylko rozpędem i zatrzymuje się po kilku jednostkach: na piasku nie ma napędu, więc nie da się nim płynąć ani wzdłuż brzegu, ani w głąb (napęd wraca dopiero, gdy dziób celuje w wodę – wystarczy się obrócić i wypłynąć). Żaden potwór nie dosięgnie łodzi na piasku, a widmokuda jej nie widzi; to awaryjna kryjówka, a nie skrót. Prąd morski nie wpycha łodzi na piasek.
- 3 gwiazdki za poziom (meta + dwa zmienne cele), mapa przygody, sklep (24 łodzie, 18 kolorowych żagli, 27 żagli z flagami państw, 26 postaci załogi – od kota i pingwina po wikinga i astronautę, 8 wzmocnień), 43 osiągnięcia, nagroda dnia (po dotknięciu karty passy w menu).
- **Kreator w sklepie** (od wersji 2.3): na dole kart ŁODZIE i ŻAGLE rozwija się „Chcesz własny kolor?” / „Utwórz własny żagiel” (karta ŻAGLE: najpierw zwykłe kolory, pod nimi flagi, na końcu kreator). Kolor wybiera się z pełnej palety (pole nasycenia i jasności, pasek barw, odczyt RGB): łódź maluje się jednym kolorem, a żagiel składa z 10 wzorów (pół na pół, pas, krzyż, szachownica, grochy…) i dwóch kolorów. Cena koloru zależy od grupy, do której wpada (`Workshop`): 8 rodzin barw × odcień pastelowy, nasycony albo ciemny oraz biel, srebro, szarość, grafit i czerń – cennik grup widać w kreatorze; wzór żagla ma osobną cenę. Projekt podobny do rzeczy ze sklepu kosztuje zawsze co najmniej o 1/4 więcej niż ona (porównanie kolorów w przestrzeni Lab, kadłub po odcieniach, żagiel pole po polu) – kreator nie jest tańszą drogą do tego samego wyglądu. Kadłub z kreatora to oryginalny sprite przemalowany w grze tymi samymi regułami co skrypt (`HullPaints`).
- **Wzmocnienia:** magnes, tarcza, turbo, dodatkowe życie, a od wersji 2.3 także sieć (łowienie z dalszej odległości), lodowa mgła (potwory o 35% wolniejsze), złota przynęta (złote ryby szybciej i na dłużej) i rytm (więcej czasu na combo). Na karcie poziomu przewijają się w bok. Żadne nie podnosi maksymalnego wyniku ani nie skraca minimalnego czasu przejazdu.
- **Sterowanie do wyboru** przy pierwszym uruchomieniu: przesuwanie palcem po całym ekranie albo klasyczny joystick w dolnym rogu (jak w popularnych grach na telefon – przeskakuje pod kciuk i wraca na miejsce po puszczeniu). Zmiana w USTAWIENIA → GRA, tam też strona joysticka (lewa / prawa).
- **Klawiatura** (tablet z klawiaturą): w trakcie poziomu łódź sterują strzałki albo WASD (także ukośnie), Esc pauzuje. Poza grą całą aplikację obsłuży się bez dotykania ekranu: strzałki i Tab przechodzą po przyciskach (wybrany ma złotą ramkę), Enter/spacja klika, Esc wraca albo zamyka okienko, a listy i długie ekrany przewijają się strzałkami i Page Up/Down. Na mapie przytrzymana strzałka w górę/dół płynnie ją przewija, a w kreatorze kolorów strzałki zmieniają paletę i pasek barw (`KeyNav`, `KeyboardSteer`).
- **Poziomy graczy – gwiazdki i linki** (od wersji 2.4):
  - Poziomom z kreatora inni gracze dają **gwiazdki** (po zagraniu, nie własnym poziomom) – jedną na telefon i jedną na konto, co sprawdza serwer; im więcej gwiazdek, tym wyżej poziom w zakładce NAJLEPSZE (domyślnej). Gra wysyła tylko skrót stałego identyfikatora telefonu (`PhoneId`), dzięki któremu ponowna instalacja nie daje drugiej gwiazdki.
  - **UDOSTĘPNIJ** prosi serwer o link i otwiera systemowe okno udostępniania: do opublikowanego poziomu (`/l/<numer>`) albo – bez publikacji, bez przechodzenia poziomu i bez konta – do szkicu z kreatora dla znajomych (`/s/<kod>`; taki poziom gra się bez punktów i monet). Przycisk jest w oknie poziomu gracza, na karcie szkicu i w oknie publikacji.
  - Link otwiera stronę pod telefony: nazwa, autor, trudność, podgląd planszy, przycisk ZAGRAJ (otwiera grę z tym poziomem, a bez gry – Google Play) i odznaka Google Play; w komunikatorach (Discord, Messenger…) link ma podgląd z obrazkiem planszy. Na telefonie z grą link otwiera się od razu w grze (Android App Links – `LevelLinks`, filtr w manifeście); gra w trakcie poziomu otwiera go po wyjściu z rozgrywki.
- **Ranking online top 100 i konto** (od wersji 2.1, serwer `Top_FishingParty` na https://fp.wojtoteka.ovh). Punkty rankingowe = suma najlepszych wyników z każdego poziomu + trofea za passę + poziomy dnia i tygodnia.
  - **Tryb rankingowy (nick + hasło)** – akceptacja polityki prywatności i regulaminu, nick i hasło. Nowy nick zakłada konto, istniejący z poprawnym hasłem loguje i przywraca postęp (np. po reinstalacji). Hasła nie da się zresetować.
  - **Konto e-mail** – e-mail + nick + hasło, potwierdzane kodem z maila (SMTP); pozwala ustawić nowe hasło kodem. Do konta „nick + hasło” można później dodać e-mail.
  - **Tylko nick** (od wersji 2.3) – sam nick, bez hasła i e-maila: telefon trzyma sesję jak przeglądarka, nick jest zajęty, a gracz od razu trafia do rankingu. Po wylogowaniu nick przepada (wynik zostaje w rankingu). Konto można rozszerzyć – najpierw o hasło, potem o e-mail.
  - Synchronizacja na bieżąco: ukończony poziom leci na serwer od razu, pełny zapis gry kilka sekund po każdej zmianie i przy wyjściu z aplikacji. Na nowym telefonie zapis jest przywracany; przy konflikcie dwóch urządzeń gra pyta, który postęp zachować.
  - Bez internetu gra działa normalnie, a ekrany KONTO i RANKING proszą o połączenie. Niewysłane wyniki czekają i lecą same po powrocie sieci.
  - Konto można usunąć w grze (KONTO → USUŃ KONTO) – serwer kasuje wszystkie dane.
- **Ogłoszenia i blokada wersji** (od wersji 2.3): przy każdym wejściu do gry z internetem – także bez konta – gra wysyła swój `versionCode` do serwera (`GET /launch`, bez podpisu, `OnlineManager.checkLaunch`, `LaunchInfo`).
  - Ogłoszenia dodaje admin w panelu serwera (tytuł i treść po polsku i angielsku, dla wszystkich, tylko gości albo tylko zalogowanych, w wybranych dniach). Gra pokazuje każde raz, w swoim języku, poza rozgrywką i przed nagrodą dzienną; po zamknięciu nie wraca (telefon pamięta zamknięte ogłoszenia, admin może kazać pokazać je ponownie).
  - Ogłoszenie oznaczone w panelu „przy każdym uruchomieniu” pokazuje się po każdym starcie gry (także po powrocie po ponad 30 min w tle), aż do daty końca albo wyłączenia. Z datą końca telefon zapisuje je (`StickyAnnouncements`) i pokazuje także bez internetu aż do tej daty, potem je usuwa; przy każdym połączeniu bierze z serwera aktualną treść i datę, a wyłączone albo usunięte w panelu znika.
  - Wersja zablokowana w panelu (np. `12`, `5-10`, `<12`) zamiast gry pokazuje komunikat z powodem (bez powodu – „ta wersja gry jest nieaktualna”) i przycisk do Google Play. To samo dzieje się, gdy serwer odpowie `426 update_required` na dowolne żądanie w trakcie gry. Bez internetu blokady nie ma – gra działa normalnie, a ze zwykłych ogłoszeń nie pokazuje się nic.
  - Statystyki w panelu serwera: to samo zapytanie niesie losowy identyfikator instalacji (`AccountStore.deviceId`, tworzony przy pierwszym uruchomieniu, także bez konta) i znacznik nowego uruchomienia. Serwer zapisuje tylko jego skrót, wersję i liczbę uruchomień na dzień.

## Ochrona wyników

- Zapis szyfrowany AES-256-GCM kluczem z Android Keystore (`SaveCrypto`, `GameRepository`) – zmiana pliku lub przeniesienie go na inne urządzenie jest wykrywane.
- Najważniejsze liczby (monety, rekordy, wynik w trakcie gry) trzymane w pamięci w postaci zamaskowanej (`GuardedLong`).
- Każdy wynik przechodzi przez `ScoreValidator`: limit maksymalnego wyniku liczony z definicji poziomu, minimalny możliwy czas, liczby ryb. Ten sam kod może później działać na serwerze rankingu (razem z Play Integrity API).
- Ostatnie zweryfikowane przejazdy (`ScoreRecord`) są wysyłane na serwer rankingu (`online/OnlineManager`). Serwer sprawdza każdy przejazd na tych samych limitach poziomów co gra (`data/level-limits.json` eksportowany przez `LevelLimitsExportTest`), pilnuje kolejności poziomów, czasu i nakładania się przejazdów, przyjmuje sygnały z gry (wykryte zmiany zapisu, edycja pamięci) i ukrywa podejrzane konta z rankingu.
- Każde żądanie do serwera jest podpisane HMAC-SHA256 (czas + jednorazowy nonce), sesja konta leży w osobnym zaszyfrowanym pliku (`AccountStore`), nie w zapisie gry.

## Grafika i dźwięk

- Tylko oryginalne sprity. Warianty kolorystyczne (palety światów, złote ryby, skórki łodzi…) robi skrypt `tools/recolor_sprites.py` z `tools/original_sprites/` – w grze przemalowywany jest tylko kadłub łodzi z kreatora kolorów.
- Muzyka i efekty: port syntezatora `audio.beep` z microStudio (`audio/Beeper.kt`) z oryginalnymi sekwencjami nut.
- Font: BitCell (memesbruh03).

## Budowanie

- Gradle 9.8.1 uruchamiany na **JDK 27** (`gradle/gradle-daemon-jvm.properties`), kompilacja przez toolchain JDK 27.
- AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 36, minSdk 26.
- Bajtkod ma target 26 – stabilny Kotlin nie obsługuje jeszcze celu 27 (pojawi się w Kotlinie 2.5).
- `app/src/main/baseline-prof.txt` – profil kompilacji: kod gry jest kompilowany z wyprzedzeniem przy instalacji wersji release (płynne menu i listy od pierwszego uruchomienia).

Ranking online wymaga w `local.properties` (plik poza repozytorium) sekretu podpisu – tego samego co `HMAC_SECRET` w `.env` serwera:

```
fp.hmacSecret=...
fp.apiUrl=https://fp.wojtoteka.ovh/api/v1   # opcjonalnie, to wartość domyślna
fp.linkHost=fp.wojtoteka.ovh                # opcjonalnie: host linków do poziomów (domyślnie host z fp.apiUrl)
```

Bez `fp.hmacSecret` gra działa, a ekrany konta i rankingu pokazują, że ranking jest niedostępny. Po zmianie generatora poziomów trzeba odświeżyć limity na serwerze: `./gradlew testDebugUnitTest --tests "*LevelLimitsExportTest*"` i skopiować `app/build/level-limits.json` do `Top_FishingParty/data/`. Zmiana `SeedLevels` (albo reguł `ChallengeKind`) wymaga tej samej zmiany w kodzie serwera – próbki `app/build/endless-sample.json` i `app/build/challenge-sample.json` z tego samego testu służą serwerowi do sprawdzenia, że obie strony liczą poziomy 1001+ oraz poziomy dnia i tygodnia tak samo.

```
./gradlew assembleDebug        # APK testowe
./gradlew testDebugUnitTest    # testy JVM (generator, symulacja z botem, zapis, szyfrowanie, podpis API)
./gradlew bundleRelease        # AAB do Google Play (wymaga konfiguracji podpisu)
```
