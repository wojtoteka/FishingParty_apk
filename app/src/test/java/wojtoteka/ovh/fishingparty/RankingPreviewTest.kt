package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
}
