package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import wojtoteka.ovh.fishingparty.online.LevelLink
import wojtoteka.ovh.fishingparty.online.LevelLinks
import wojtoteka.ovh.fishingparty.online.PhoneId

/** Linki do poziomów z kreatora otwierane w grze i identyfikator telefonu do gwiazdek. */
class LevelLinksTest {
    private val host = "fp.example.org"

    @Test
    fun publishedAndSharedLinks() {
        assertEquals(LevelLink.Published(42), LevelLinks.parse("https://fp.example.org/l/42", host))
        assertEquals(LevelLink.Published(42), LevelLinks.parse("https://FP.example.org/l/42/?lang=en", host))
        assertEquals(LevelLink.Published(7), LevelLinks.parse(" http://fp.example.org/l/7 ", host))
        assertEquals(LevelLink.Shared("k3J9xQ2mPa"), LevelLinks.parse("https://fp.example.org/s/k3J9xQ2mPa", host))
        assertEquals(LevelLink.Shared("k3J9xQ2mPa"), LevelLinks.parse("https://fp.example.org/s/k3J9xQ2mPa?lang=en#x", host))
    }

    @Test
    fun foreignOrBrokenLinksAreIgnored() {
        for (link in listOf(
            null,
            "",
            "https://evil.example.org/l/42",
            "https://fp.example.org.evil.com/l/42",
            "ftp://fp.example.org/l/42",
            "https://fp.example.org/l/0",
            "https://fp.example.org/l/abc",
            "https://fp.example.org/l/42/map.png",
            "https://fp.example.org/s/short",
            "https://fp.example.org/s/k3J9xQ2mP0",
            "https://fp.example.org/s/k3J9xQ2mPal",
            "https://fp.example.org/admin",
            "not a link at all",
        )) {
            assertNull(link, LevelLinks.parse(link, host))
        }
    }

    @Test
    fun betaLinksOnlyInDebugBuild() {
        // Wersja debug (beta serwera) otwiera tylko linki z /beta, oficjalna gra – tylko bez niego.
        assertEquals(LevelLink.Published(42), LevelLinks.parse("https://fp.example.org/beta/l/42", host, "/beta"))
        assertEquals(LevelLink.Shared("k3J9xQ2mPa"), LevelLinks.parse("https://fp.example.org/beta/s/k3J9xQ2mPa?lang=en", host, "/beta"))
        assertNull(LevelLinks.parse("https://fp.example.org/l/42", host, "/beta"))
        assertNull(LevelLinks.parse("https://fp.example.org/betal/42", host, "/beta"))
        assertNull(LevelLinks.parse("https://fp.example.org/beta/l/42", host))
    }

    @Test
    fun phoneIdIsStableHash() {
        val a = PhoneId.hash("9774d56d682e549c")
        assertEquals(64, a.length)
        assertEquals(a, PhoneId.hash("9774d56d682e549c"))
        assertNotEquals(a, PhoneId.hash("9774d56d682e549d"))
        assert(a.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
