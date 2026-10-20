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
}
