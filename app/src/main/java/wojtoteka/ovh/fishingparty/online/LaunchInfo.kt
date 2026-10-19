package wojtoteka.ovh.fishingparty.online

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ogłoszenie z panelu serwera – tytuł i treść po polsku i angielsku. Gra pokazuje je raz, w języku aplikacji.
 * [revision] rośnie, gdy admin każe pokazać ogłoszenie ponownie także tym, którzy już je zamknęli.
 * [persistent] – pokazywane przy każdym uruchomieniu gry aż do [endsAt] (null = do wyłączenia w panelu).
 */
data class Announcement(
    val id: Int,
    val revision: Int,
    val titlePl: String,
    val titleEn: String,
    val bodyPl: String,
    val bodyEn: String,
    val persistent: Boolean = false,
    val endsAt: Long? = null,
) {
    /** Pod tym kluczem telefon pamięta, że gracz zamknął ogłoszenie. */
    val key: String get() = "$id.$revision"

    fun title(polish: Boolean): String = pick(polish, titlePl, titleEn)

    fun body(polish: Boolean): String = pick(polish, bodyPl, bodyEn)
}

/** Wersja gry zablokowana w panelu. Bez powodu gra mówi tylko, że wersja jest nieaktualna. */
data class VersionBlock(val reasonPl: String?, val reasonEn: String?) {
    /** Powód w języku gry, a gdy admin wpisał go tylko w jednym języku – ten jeden. */
    fun reason(polish: Boolean): String? = if (polish) reasonPl ?: reasonEn else reasonEn ?: reasonPl
}

/** Odpowiedź `GET /launch`: blokada tej wersji (null = można grać) i bieżące ogłoszenia. */
class LaunchInfo(val block: VersionBlock?, val announcements: List<Announcement>) {
    companion object {
        fun parse(json: JSONObject): LaunchInfo {
            val version = json.optJSONObject("version")
            val block = if (version?.optBoolean("blocked") == true) blockOf(version) else null
            return LaunchInfo(block, announcements(json.optJSONArray("announcements")))
        }

        /** Lista ogłoszeń w formacie serwera (`announcements` z `/launch`) – tak zapisuje je też telefon. */
        fun announcements(list: JSONArray?): List<Announcement> =
            (0 until (list?.length() ?: 0)).mapNotNull { i -> list?.optJSONObject(i)?.let(::announcementOf) }

        fun toJson(list: List<Announcement>): JSONArray = JSONArray().apply {
            for (a in list) {
                put(
                    JSONObject()
                        .put("id", a.id)
                        .put("revision", a.revision)
                        .put("title", JSONObject().put("pl", a.titlePl).put("en", a.titleEn))
                        .put("body", JSONObject().put("pl", a.bodyPl).put("en", a.bodyEn))
                        .put("persistent", a.persistent)
                        .put("endsAt", a.endsAt ?: JSONObject.NULL),
                )
            }
        }

        /** Powód blokady z odpowiedzi serwera (`/launch` albo `426 update_required` podpisanego żądania). */
        fun blockOf(json: JSONObject): VersionBlock {
            val reason = json.optJSONObject("reason")
            return VersionBlock(reason?.text("pl"), reason?.text("en"))
        }

        private fun announcementOf(o: JSONObject): Announcement? {
            val title = o.optJSONObject("title") ?: return null
            val body = o.optJSONObject("body") ?: return null
            if (!o.has("id")) return null
            val a = Announcement(
                id = o.optInt("id"),
                revision = o.optInt("revision", 1),
                titlePl = title.text("pl").orEmpty(),
                titleEn = title.text("en").orEmpty(),
                bodyPl = body.text("pl").orEmpty(),
                bodyEn = body.text("en").orEmpty(),
                persistent = o.optBoolean("persistent"),
                endsAt = if (o.isNull("endsAt")) null else o.optLong("endsAt").takeIf { it > 0 },
            )
            return a.takeIf { (it.titlePl.isNotEmpty() || it.titleEn.isNotEmpty()) && (it.bodyPl.isNotEmpty() || it.bodyEn.isNotEmpty()) }
        }

        /** Tekst pola albo null (brak, JSON null albo same spacje). */
        private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() }
    }
}

/** Które ogłoszenia gracz już zamknął – lista kluczy [Announcement.key], najnowsze na końcu. */
object SeenAnnouncements {
    /** Tyle ostatnio zamkniętych ogłoszeń pamięta telefon. */
    const val LIMIT = 100

    fun unseen(list: List<Announcement>, seen: Collection<String>): List<Announcement> = list.filter { it.key !in seen }

    /**
     * Co pokazać teraz: zwykłe ogłoszenia, których gracz jeszcze nie zamknął ([seen]), i ogłoszenia
     * „przy każdym uruchomieniu”, których nie zamknął od startu gry ([shownThisLaunch]) – bez tych po dacie końca.
     */
    fun queue(list: List<Announcement>, seen: Collection<String>, shownThisLaunch: Collection<String>, now: Long): List<Announcement> =
        list.filter { a ->
            val live = a.endsAt == null || a.endsAt > now
            live && if (a.persistent) a.key !in shownThisLaunch else a.key !in seen
        }

    fun remember(seen: List<String>, key: String): List<String> = (seen - key + key).takeLast(LIMIT)

    fun decode(text: String?): List<String> = text.orEmpty().split(',').filter { it.isNotBlank() }

    fun encode(seen: List<String>): String = seen.joinToString(",")
}

/**
 * Ogłoszenia „przy każdym uruchomieniu” z datą końca zapisane w telefonie – pokazują się też bez internetu
 * aż do tej daty, potem są usuwane. Przy każdym połączeniu zastępuje je lista z serwera (nowa data, treść,
 * ogłoszenie wyłączone albo usunięte w panelu znika).
 */
object StickyAnnouncements {
    /** Które ogłoszenia z serwera telefon zapamiętuje. */
    fun keep(list: List<Announcement>): List<Announcement> = list.filter { it.persistent && it.endsAt != null }

    /** Zapisane ogłoszenia, których data końca jeszcze nie minęła. */
    fun valid(list: List<Announcement>, now: Long): List<Announcement> = list.filter { a -> a.persistent && a.endsAt != null && a.endsAt > now }

    fun encode(list: List<Announcement>): String = LaunchInfo.toJson(list).toString()

    fun decode(text: String?): List<Announcement> = try {
        if (text.isNullOrBlank()) emptyList() else LaunchInfo.announcements(JSONArray(text))
    } catch (_: org.json.JSONException) {
        emptyList()
    }
}

private fun pick(polish: Boolean, pl: String, en: String): String = if (polish) pl.ifEmpty { en } else en.ifEmpty { pl }
