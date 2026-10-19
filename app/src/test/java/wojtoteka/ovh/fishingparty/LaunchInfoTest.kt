package wojtoteka.ovh.fishingparty

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.online.Announcement
import wojtoteka.ovh.fishingparty.online.LaunchInfo
import wojtoteka.ovh.fishingparty.online.SeenAnnouncements
import wojtoteka.ovh.fishingparty.online.VersionBlock

/** Odpowiedź serwera przy starcie gry (`GET /launch`): blokada wersji i ogłoszenia. */
class LaunchInfoTest {

    private val response = """
        {"now": 1792407644793,
         "version": {"blocked": false},
         "announcements": [
           {"id": 3, "revision": 1, "title": {"pl": "Nowy sezon", "en": "New season"},
            "body": {"pl": "Podwójne monety\ndo niedzieli!", "en": "Double coins\nuntil Sunday!"}, "endsAt": 1793012400000},
           {"id": 4, "revision": 2, "title": {"pl": "Przerwa", "en": "Maintenance"},
            "body": {"pl": "Serwer w nocy nie działa.", "en": "Server down tonight."}, "endsAt": null}
         ]}
    """.trimIndent()

    @Test
    fun announcementsInBothLanguages() {
        val info = LaunchInfo.parse(JSONObject(response))
        assertNull(info.block)
        assertEquals(listOf("3.1", "4.2"), info.announcements.map { it.key })
        val first = info.announcements.first()
        assertEquals("Nowy sezon", first.title(polish = true))
        assertEquals("New season", first.title(polish = false))
        assertEquals("Podwójne monety\ndo niedzieli!", first.body(polish = true))
        assertEquals("Double coins\nuntil Sunday!", first.body(polish = false))
    }

    @Test
    fun missingLanguageFallsBackToTheOther() {
        val a = Announcement(1, 1, "Tylko po polsku", "", "Treść", "")
        assertEquals("Tylko po polsku", a.title(polish = false))
        assertEquals("Treść", a.body(polish = false))
        // Nieprawidłowe wpisy (bez tytułu albo treści w żadnym języku, bez id) są pomijane.
        val json = JSONObject(
            """{"version": {"blocked": false}, "announcements": [
                {"id": 1, "title": {"pl": "", "en": null}, "body": {"pl": "x", "en": "x"}},
                {"id": 2, "title": {"pl": "T", "en": "T"}, "body": {"pl": "  ", "en": null}},
                {"title": {"pl": "T", "en": "T"}, "body": {"pl": "B", "en": "B"}},
                {"id": 5, "title": {"pl": "", "en": "Only English"}, "body": {"pl": null, "en": "Text"}},
                "zepsute", null
            ]}""",
        )
        val list = LaunchInfo.parse(json).announcements
        assertEquals(listOf(5), list.map { it.id })
        assertEquals("Only English", list[0].title(polish = true))
        // Brak revision = 1.
        assertEquals("5.1", list[0].key)
    }

    @Test
    fun blockedVersionWithReason() {
        val json = JSONObject("""{"version": {"blocked": true, "reason": {"pl": "Błąd w zapisie gry.", "en": null}}, "announcements": []}""")
        val block = LaunchInfo.parse(json).block!!
        assertEquals("Błąd w zapisie gry.", block.reason(polish = true))
        // Powód tylko po polsku – angielski gracz dostaje ten jeden.
        assertEquals("Błąd w zapisie gry.", block.reason(polish = false))
        assertEquals("Please update", VersionBlock("Zaktualizuj", "Please update").reason(polish = false))
    }

    @Test
    fun blockedVersionWithoutReasonAndFrom426() {
        // Bez powodu (np. wersja poniżej MIN_APP_VERSION) – gra mówi „wersja nieaktualna”.
        val bare = LaunchInfo.parse(JSONObject("""{"version": {"blocked": true, "reason": {"pl": null, "en": null}}}""")).block!!
        assertNull(bare.reason(polish = true))
        assertNull(bare.reason(polish = false))
        // Odpowiedź 426 podpisanego żądania: ten sam format powodu, a stary serwer w ogóle go nie podaje.
        val refused = LaunchInfo.blockOf(JSONObject("""{"error": "update_required", "minAppVersion": 10, "blocked": true, "reason": {"pl": "  ", "en": "Update"}}"""))
        assertEquals(VersionBlock(null, "Update"), refused)
        assertEquals(VersionBlock(null, null), LaunchInfo.blockOf(JSONObject("""{"error": "update_required", "minAppVersion": 10}""")))
        // Odpowiedź bez pól (starszy serwer, nieznany format) – nic nie jest zablokowane ani ogłoszone.
        val empty = LaunchInfo.parse(JSONObject("{}"))
        assertNull(empty.block)
        assertTrue(empty.announcements.isEmpty())
    }

    @Test
    fun closedAnnouncementsAreNotShownAgain() {
        val list = LaunchInfo.parse(JSONObject(response)).announcements
        var seen = SeenAnnouncements.decode(null)
        assertEquals(list, SeenAnnouncements.unseen(list, seen))
        seen = SeenAnnouncements.remember(seen, "3.1")
        seen = SeenAnnouncements.decode(SeenAnnouncements.encode(seen))
        assertEquals(listOf("4.2"), SeenAnnouncements.unseen(list, seen).map { it.key })
        // „Pokaż ponownie” w panelu podnosi rewizję – zamknięte ogłoszenie wraca.
        val reshown = list.map { if (it.id == 3) it.copy(revision = 2) else it }
        assertEquals(listOf("3.2", "4.2"), SeenAnnouncements.unseen(reshown, seen).map { it.key })
        // Ponowne zamknięcie nie dubluje wpisu, a lista ma limit (najstarsze odpadają).
        assertEquals(listOf("3.1"), SeenAnnouncements.remember(seen, "3.1"))
        var many = emptyList<String>()
        for (i in 1..SeenAnnouncements.LIMIT + 20) many = SeenAnnouncements.remember(many, "$i.1")
        assertEquals(SeenAnnouncements.LIMIT, many.size)
        assertEquals("21.1", many.first())
        assertEquals("${SeenAnnouncements.LIMIT + 20}.1", many.last())
    }
}
