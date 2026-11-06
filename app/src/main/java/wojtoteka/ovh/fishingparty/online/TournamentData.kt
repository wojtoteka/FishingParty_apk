package wojtoteka.ovh.fishingparty.online

import org.json.JSONArray
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.Tournament
import wojtoteka.ovh.fishingparty.level.Tournaments

/** Nagroda za miejsca [from]..[to] (to = null – każde dalsze miejsce): premia do rankingu i monety. */
class TournamentPrize(val from: Int, val to: Int?, val points: Int, val coins: Int)

/** Gracz w rankingu turnieju. [prizeCoins] / [prizePoints] – po rozliczeniu turnieju (wcześniej 0). */
class TournamentEntry(val rank: Int, val id: Long, val nick: String, val score: Int, val prizeCoins: Int = 0, val prizePoints: Int = 0)

/** Wynik tego gracza w turnieju: najlepszy wynik, miejsce (null – jeszcze bez wyniku) i nagroda po rozliczeniu. */
class TournamentMe(val score: Int, val rank: Int?, val prizeCoins: Int, val prizePoints: Int)

/**
 * Odpowiedź `GET /tournaments`: trwający turniej ([current], z seedem), najbliższy ([next]) i poprzedni z podium.
 * [prizes] – nagrody za miejsca (te same dla każdego turnieju).
 */
class TournamentInfo(
    val now: Long,
    val current: Tournament?,
    val next: Tournament?,
    val previous: Tournament?,
    val previousFinalized: Boolean,
    val previousPlayers: Int,
    val previousTop: List<TournamentEntry>,
    val prizes: List<TournamentPrize>,
)

/** Ranking turnieju (`GET /tournaments/:id/ranking`). [myId] = null – ogląda gość bez konta. */
class TournamentBoard(
    val tournament: Tournament,
    val finalized: Boolean,
    val participants: Int,
    val entries: List<TournamentEntry>,
    val me: TournamentMe?,
    val myId: Long?,
)

/** Odczyt odpowiedzi serwera o turniejach – bez Androida, więc testowalny na JVM. */
object TournamentJson {

    fun info(json: JSONObject): TournamentInfo {
        val previous = json.optJSONObject("previous")
        return TournamentInfo(
            now = json.optLong("now"),
            current = json.optJSONObject("current")?.let { tournament(it) },
            next = json.optJSONObject("next")?.let { tournament(it) },
            previous = previous?.let { tournament(it) },
            previousFinalized = previous?.optBoolean("finalized") == true,
            previousPlayers = previous?.optInt("participants") ?: 0,
            previousTop = previous?.optJSONArray("top")?.let { entries(it) }.orEmpty(),
            prizes = json.optJSONArray("prizes")?.let { prizes(it) }.orEmpty(),
        )
    }

    fun board(json: JSONObject): TournamentBoard? {
        val tournament = json.optJSONObject("tournament")?.let { tournament(it) } ?: return null
        val me = json.optJSONObject("me")
        return TournamentBoard(
            tournament = tournament,
            finalized = json.optBoolean("finalized"),
            participants = json.optInt("participants"),
            entries = json.optJSONArray("entries")?.let { entries(it) }.orEmpty(),
            me = me?.let {
                val prize = it.optJSONObject("prize")
                TournamentMe(
                    score = it.optInt("score"),
                    rank = if (it.isNull("rank") || !it.has("rank")) null else it.optInt("rank"),
                    prizeCoins = prize?.optInt("coins") ?: 0,
                    prizePoints = prize?.optInt("points") ?: 0,
                )
            },
            myId = me?.optLong("id")?.takeIf { me.has("id") },
        )
    }

    /** Turniej z odpowiedzi; seed i reguły świata tylko w trwającym (bez nich [Tournament.playable] = false). */
    fun tournament(o: JSONObject): Tournament? {
        if (!o.has("id")) return null
        val startsAt = o.optLong("startsAt")
        val endsAt = o.optLong("endsAt")
        if (endsAt <= startsAt) return null
        val name = o.optJSONObject("name")
        val p = o.optJSONObject("profile")
        val profile = p?.let {
            Tournaments.profileOf(it.optInt("minDifficulty", -1), it.optInt("maxDifficulty", -1), it.optInt("minStages", -1), it.optInt("maxStages", -1))
        }
        // Seed jest tekstem – 64 bity nie mieszczą się w liczbie JSON bez utraty dokładności.
        val seed = o.optString("seed").takeIf { !o.isNull("seed") }?.toLongOrNull()?.let { LevelSeed(it) }
        return Tournament(
            id = o.optInt("id"),
            theme = o.optString("theme"),
            namePl = name?.optString("pl").orEmpty(),
            nameEn = name?.optString("en").orEmpty(),
            startsAt = startsAt,
            endsAt = endsAt,
            seed = if (profile != null) seed else null,
            profile = if (seed != null) profile else null,
            position = o.optInt("position"),
        )
    }

    private fun entries(list: JSONArray): List<TournamentEntry> = (0 until list.length()).mapNotNull { i ->
        val e = list.optJSONObject(i) ?: return@mapNotNull null
        val prize = e.optJSONObject("prize")
        TournamentEntry(
            rank = e.optInt("rank"),
            id = e.optLong("id"),
            nick = e.optString("nick"),
            score = e.optInt("score"),
            prizeCoins = prize?.optInt("coins") ?: 0,
            prizePoints = prize?.optInt("points") ?: 0,
        )
    }

    private fun prizes(list: JSONArray): List<TournamentPrize> = (0 until list.length()).mapNotNull { i ->
        val p = list.optJSONObject(i) ?: return@mapNotNull null
        TournamentPrize(
            from = p.optInt("from"),
            to = if (p.isNull("to") || !p.has("to")) null else p.optInt("to"),
            points = p.optInt("points"),
            coins = p.optInt("coins"),
        )
    }
}
