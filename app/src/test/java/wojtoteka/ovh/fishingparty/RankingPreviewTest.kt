package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import wojtoteka.ovh.fishingparty.online.RankingEntry
import wojtoteka.ovh.fishingparty.online.RankingPreview

class RankingPreviewTest {
    @Test
    fun guestPlaceInTopList() {
        val top = listOf(9000L, 5000, 5000, 1200, 300)
        assertEquals(1, RankingPreview.place(9500, top, 100))
        // Remis: starszy wynik wyżej, nowe konto staje za wszystkimi z tą samą liczbą punktów.
        assertEquals(4, RankingPreview.place(5000, top, 100))
        assertEquals(6, RankingPreview.place(10, top, 100))
        // Bez punktów serwer nie pokazuje konta w rankingu.
        assertNull(RankingPreview.place(0, top, 100))
        // Pełny ranking: za słaby wynik zostaje poza listą, lepszy wchodzi na swoje miejsce.
        val full = (1..100).map { 10_000L - it * 10 }
        assertNull(RankingPreview.place(5, full, 100))
        assertEquals(100, RankingPreview.place(9_005, full, 100))
        assertEquals(51, RankingPreview.place(9_495, full, 100))
    }

    @Test
    fun leadersWithGuest() {
        val entries = listOf(9000L, 5000, 1200, 300).mapIndexed { i, p -> RankingEntry(i + 1, 100L + i, "g$i", p, 10) }
        fun rows(place: Int?) = RankingPreview.leaders(entries, place, 3).map { it.place to it.entry?.id }
        // Bez gościa (bez punktów) – sama czołówka.
        assertEquals(listOf(1 to 100L, 2 to 101L, 3 to 102L), rows(null))
        // Gość w czołówce wypycha ostatniego z niej, gracze za nim przesuwają się o miejsce.
        assertEquals(listOf(1 to 100L, 2 to null, 3 to 101L), rows(2))
        assertEquals(listOf(1 to null, 2 to 100L, 3 to 101L), rows(1))
        assertEquals(listOf(1 to 100L, 2 to 101L, 3 to null), rows(3))
        // Gość dalej – czołówka bez zmian, on na końcu.
        assertEquals(listOf(1 to 100L, 2 to 101L, 3 to 102L, 5 to null), rows(5))
        // Pusty ranking: gość na pierwszym miejscu.
        assertEquals(listOf(1 to null), RankingPreview.leaders(emptyList(), 1, 3).map { it.place to it.entry?.id })
    }
}
