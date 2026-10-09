# JUŻ NIEDŁUGO NOWA AKTUALIZACJA
## Fishing Party

Podwodna zręcznościówka na Androida: pływasz między piranhami, ośmiornicami i rekinami,
zbierasz ryby i szukasz wyjścia z ośmiu ręcznie ułożonych map. Retro pixel art, sterowanie
dotykiem, pion.

Gra powstała w [microStudio](https://microstudio.dev) i jest tu opakowana we własną,
natywną aplikację Androida - Kotlin + Jetpack Compose, z WebView jako płótnem.

---

## Dlaczego natywny wrapper, a nie sam PWA

Sama gra to eksport HTML5. Żeby zachowywała się jak normalna aplikacja na telefonie, wrapper
dokłada rzeczy, których przeglądarka nie da:

**Pełny ekran bez ustrojstw.** Immersive mode przez `WindowInsetsController`, wycięcie na
aparat, blokada orientacji na pionie, `WAKE_LOCK` żeby ekran nie gasł w trakcie gry,
i przechwycony gest „wstecz", który nie wyrzuca gracza z rozgrywki w połowie planszy.

**Pilnowanie pamięci.** `WebViewMemoryManager` reaguje na `onTrimMemory` i zabija znane
zachowanie WebView na słabszych telefonach - proces renderujący pada, a użytkownik dostaje
biały ekran. Zamiast tego widok wstaje z powrotem sam.

**Odzyskiwanie po awarii.** Własny `UncaughtExceptionHandler` i `CrashHandler`: gdy proces
WebView padnie, apka nie zamyka się w ciszy, tylko odtwarza widok i pokazuje komunikat.

**Ekran startowy.** Compose'owy ekran ładowania z animacją, trzymany do momentu, aż gra
faktycznie wystartuje - bez migającej białej pustki.

**Zero sieci.** Gra jedzie w całości z `assets/`, więc działa offline i nie prosi
o uprawnienie do internetu.

---

## Układ

```
app/src/main/java/.../MainActivity.kt   wrapper: WebView, pełny ekran, pamięć, awarie
app/src/main/java/.../ui/theme/         motyw Compose (ekran ładowania)
app/src/main/assets/game/               gra wyeksportowana z microStudio
  ├─ microengine.js, runner_v1_i.js     runtime microStudio
  ├─ program.js, parser.js              logika gry
  └─ sprites/ (57 plików), fonts/, mapy 20×20
```

## Budowanie

```bash
./gradlew assembleDebug
```

`local.properties` (ścieżka do SDK) jest lokalne i nie trafia do repozytorium.

- `minSdk` 24, `targetSdk` 36, Java 11
- wersja 1.4 (`versionCode` 5)

## Licencja

Kod udostępniony do wglądu w celach portfolio. Runtime w `assets/game/`
(`microengine.js`, `runner_v1_i.js`) pochodzi z microStudio i podlega jego licencji.
