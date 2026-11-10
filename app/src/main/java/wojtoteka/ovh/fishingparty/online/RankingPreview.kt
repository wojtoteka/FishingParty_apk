package wojtoteka.ovh.fishingparty.online

/**
 * Podgląd dla gracza bez konta: ile miałby punktów w rankingu i które miejsce by zajął,
 * gdyby teraz założył konto. Punkty liczy gra (te same zasady co serwer), miejsce – z top listy.
 */
object RankingPreview {
    /**
     * Miejsce w top [size] dla [points] punktów albo null (poza top albo bez punktów – serwer
     * nie pokazuje kont z zerem). Przy remisie wyżej stoi starszy wynik, więc nowe konto
     * ustawia się za wszystkimi z tą samą liczbą punktów.
     */
    fun place(points: Long, top: List<Long>, size: Int): Int? {
        if (points <= 0) return null
        val place = top.count { it >= points } + 1
        return if (place <= size) place else null
    }

    /**
     * Czołówka rankingu – pierwsze [count] miejsc z gościem wstawionym na miejsce [place] (z [place]).
     * Gracze za gościem przesuwają się o miejsce w dół. Gość spoza czołówki stoi na końcu, za nią.
     */
    fun leaders(entries: List<RankingEntry>, place: Int?, count: Int): List<PreviewRow> {
        val rows = entries.take(count).map { e -> PreviewRow(if (place != null && e.rank >= place) e.rank + 1 else e.rank, e) }
        return when {
            place == null -> rows
            place <= count -> rows.toMutableList().apply { add((place - 1).coerceAtMost(size), PreviewRow(place, null)) }.take(count)
            else -> rows + PreviewRow(place, null)
        }
    }
}

/** Wiersz skróconego rankingu: gracz z top listy albo gość bez konta ([entry] == null). */
class PreviewRow(val place: Int, val entry: RankingEntry?)
