package wojtoteka.ovh.fishingparty.online

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.data.GameRepository
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.LevelDraft
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.data.UserLevelPlay
import wojtoteka.ovh.fishingparty.data.Workshop
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevels
import java.security.SecureRandom

/** Poziom gracza na serwerze (bez planszy). [tier] – stopień trudności 0..6, [points] – punkty rankingu za ukończenie. */
class OnlineLevel(
    val id: Int,
    val title: String,
    val creatorId: Long,
    val creator: String,
    val theme: Int,
    val stages: Int,
    val difficulty: Int,
    val contentTier: Int,
    val tier: Int,
    val rated: Boolean,
    val points: Int,
    val copyable: Boolean,
    val attempts: Int,
    val players: Int,
    val completions: Int,
    val likes: Int,
    val dislikes: Int,
    val featured: Boolean,
    val hidden: Boolean = false,
    val hiddenReason: String? = null,
) {
    /** Jaka część graczy ukończyła poziom (w procentach), null – nikt jeszcze nie grał. */
    val completionRate: Int? get() = if (players > 0) completions * 100 / players else null
}

class LevelPage(val levels: List<OnlineLevel>, val page: Int, val more: Boolean)

/** Stan tego telefonu na poziomie: próby, ukończenie, rekord, głos. [creator] – to poziom tego gracza. */
class PlayerState(val attempts: Int, val completed: Boolean, val best: Int, val vote: Int, val reported: Boolean, val creator: Boolean)

class LevelDetails(val level: OnlineLevel, val data: UserLevel, val me: PlayerState?)

/**
 * Odpowiedź serwera na przejazd: monety za pierwsze ukończenie (już dodane do zapisu), punkty rankingu
 * za ten poziom (0 – poziom bez oceny albo ponad dzienny limit) i aktualne liczby poziomu.
 */
class PlayOutcome(
    val firstCompletion: Boolean,
    val coins: Int,
    val points: Int,
    val overDailyLimit: Boolean,
    val level: OnlineLevel,
)

/** Projekt na targu: [design] to id rzeczy z kreatora (np. „own-1E90FF”). */
class MarketItem(
    val id: Int,
    val kind: ItemKind,
    val design: String,
    val name: String,
    val price: Int,
    val sales: Int,
    val sellerId: Long,
    val seller: String,
    val active: Boolean = true,
    val earned: Long = 0,
) {
    /** Rzecz z kreatora, którą projekt daje kupującemu. */
    val item: ShopItem? get() = Workshop.find("${kind.name.lowercase()}:$design")
}

class MarketPage(val items: List<MarketItem>, val page: Int, val more: Boolean)

class MyMarket(val items: List<MarketItem>, val pending: Long)

/** Po tylu graczach (bez autora) poziom dostaje ocenę i daje punkty rankingu (USER_LEVEL_MIN_PLAYERS serwera). */
const val COMMUNITY_MIN_PLAYERS = 5

/** Wynik żądania: wartość albo błąd (kod serwera albo `offline`). */
sealed interface Net<out T> {
    data class Ok<T>(val value: T) : Net<T>
    data class Err(val failure: OnlineResult.Failed) : Net<Nothing>
}

/**
 * Poziomy graczy i targ. Przeglądać i grać może każdy (żądania podpisuje klucz telefonu), publikować poziomy
 * i sprzedawać projekty – tylko z kontem. Przejazdy poziomów graczy czekają w zapisie gry, dopóki serwer ich nie
 * przyjmie – monety za pierwsze ukończenie dodajemy dopiero po jego odpowiedzi.
 */
class Community internal constructor(private val online: OnlineManager, private val repo: GameRepository) {
    private val flushLock = Mutex()
    private val claimLock = Mutex()

    /** Odpowiedzi serwera na wysłane przejazdy (klucz – czas przejazdu), np. dla ekranu wygranej. */
    val outcomes = mutableStateMapOf<Long, PlayOutcome>()

    private suspend fun <T> request(block: suspend () -> T): Net<T> = try {
        if (!online.isOnline()) Net.Err(OnlineResult.Failed("offline")) else Net.Ok(block())
    } catch (e: Exception) {
        Net.Err(online.failureOf(e))
    }

    // ------------------------------------------------------------------ poziomy

    suspend fun levels(sort: String, page: Int, query: String): Net<LevelPage> = request {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val res = online.call("GET", "/levels?sort=$sort&page=$page&q=$q", auth = false, device = false)
        val list = res.getJSONArray("levels")
        LevelPage(List(list.length()) { parseLevel(list.getJSONObject(it)) }, res.optInt("page"), res.optBoolean("more"))
    }

    suspend fun level(id: Int): Net<LevelDetails> = request {
        val res = online.call("GET", "/levels/$id?device=${online.deviceId}", auth = online.loggedIn)
        val o = res.getJSONObject("level")
        val data = UserLevels.decode(o.getString("data")) ?: throw ApiException(0, "level_format", JSONObject())
        val me = res.optJSONObject("me")?.let {
            PlayerState(it.optInt("attempts"), it.optBoolean("completed"), it.optInt("best"), it.optInt("vote"), it.optBoolean("reported"), it.optBoolean("creator"))
        }
        if (me?.completed == true) repo.progress.markUserLevelDone(id)
        LevelDetails(parseLevel(o), data, me)
    }

    suspend fun mine(): Net<List<OnlineLevel>> = request {
        val list = online.call("GET", "/levels/mine").getJSONArray("levels")
        List(list.length()) { parseLevel(list.getJSONObject(it)) }
    }

    /** Publikacja zweryfikowanego szkicu; zwraca poziom z numerem na serwerze. */
    suspend fun publish(draft: LevelDraft, copyable: Boolean): Net<OnlineLevel> = request {
        val run = draft.verifyRun ?: throw ApiException(0, "not_verified", JSONObject())
        val body = JSONObject()
            .put("deviceId", online.deviceId)
            .put("data", UserLevels.encode(draft.level))
            .put("copyable", copyable)
            .put(
                "verify",
                JSONObject().put("score", run.score).put("timeMs", run.timeMillis).put("common", run.common).put("blue", run.blue)
                    .put("golden", run.golden).put("hits", run.hits).put("maxCombo", run.maxCombo).put("stages", run.stages).put("at", run.at),
            )
        parseLevel(online.call("POST", "/levels", body).getJSONObject("level"))
    }

    suspend fun delete(id: Int): Net<Unit> = request {
        online.applyProfileOf(online.call("POST", "/levels/$id/delete"))
    }

    suspend fun vote(id: Int, value: Int): Net<OnlineLevel> = request {
        parseLevel(online.call("POST", "/levels/$id/vote", JSONObject().put("deviceId", online.deviceId).put("value", value), auth = online.loggedIn).getJSONObject("level"))
    }

    suspend fun report(id: Int): Net<Unit> = request {
        online.call("POST", "/levels/$id/report", JSONObject().put("deviceId", online.deviceId), auth = online.loggedIn)
        Unit
    }

    /**
     * Wysyła czekające przejazdy po kolei. Przejazd odrzucony przez serwer (np. niemożliwy wynik albo usunięty poziom)
     * wypada z kolejki; brak internetu albo kłopot serwera – zostaje na następną próbę.
     */
    suspend fun flush() {
        if (!online.isOnline() || !online.configured) return
        flushLock.withLock {
            while (true) {
                val play = repo.save.userLevelQueue.firstOrNull() ?: return
                val keep = try {
                    val res = online.call("POST", "/levels/${play.levelId}/play", playBody(play), auth = online.loggedIn)
                    if (!res.optBoolean("duplicate")) {
                        val outcome = PlayOutcome(
                            res.optBoolean("firstCompletion"),
                            res.optInt("coins"),
                            res.optInt("points"),
                            res.optBoolean("overDailyLimit"),
                            parseLevel(res.getJSONObject("level")),
                        )
                        if (outcome.coins > 0) repo.progress.addCoins(outcome.coins.toLong())
                        outcomes[play.at] = outcome
                    }
                    online.applyProfileOf(res)
                    false
                } catch (e: OfflineException) {
                    true
                } catch (e: ApiException) {
                    // Poza błędami przejazdu (400 – serwer go odrzucił, 404 – poziomu już nie ma) czekamy na lepszy moment.
                    if (e.status != 400 && e.status != 404) true else {
                        Log.w("FishingParty", "Przejazd poziomu ${play.levelId} odrzucony: ${e.code}")
                        false
                    }
                } catch (e: Exception) {
                    Log.w("FishingParty", "Wysyłka przejazdu poziomu gracza", e)
                    true
                }
                if (keep) return
                repo.save.userLevelQueue.remove(play)
                repo.changed()
            }
        }
    }

    private fun playBody(p: UserLevelPlay) = JSONObject()
        .put("deviceId", online.deviceId)
        .put("completed", p.completed)
        .put(
            "run",
            JSONObject().put("score", p.score).put("timeMs", p.timeMillis).put("common", p.common).put("blue", p.blue)
                .put("golden", p.golden).put("hits", p.hits).put("maxCombo", p.maxCombo).put("stages", p.stages).put("at", p.at),
        )

    private fun parseLevel(o: JSONObject): OnlineLevel {
        val creator = o.getJSONObject("creator")
        return OnlineLevel(
            id = o.getInt("id"),
            title = o.getString("title"),
            creatorId = creator.getLong("id"),
            creator = creator.getString("nick"),
            theme = o.optInt("theme"),
            stages = o.optInt("stages"),
            difficulty = o.optInt("difficulty"),
            contentTier = o.optInt("contentTier"),
            tier = o.optInt("tier"),
            rated = o.optBoolean("rated"),
            points = o.optInt("points"),
            copyable = o.optBoolean("copyable"),
            attempts = o.optInt("attempts"),
            players = o.optInt("players"),
            completions = o.optInt("completions"),
            likes = o.optInt("likes"),
            dislikes = o.optInt("dislikes"),
            featured = o.optBoolean("featured"),
            hidden = o.optBoolean("hidden"),
            hiddenReason = o.optString("hiddenReason").takeIf { !o.isNull("hiddenReason") && it.isNotEmpty() },
        )
    }

    // ------------------------------------------------------------------ targ

    suspend fun market(kind: ItemKind?, sort: String, page: Int): Net<MarketPage> = request {
        val k = when (kind) {
            ItemKind.BOAT -> "&kind=boat"
            ItemKind.SAIL -> "&kind=sail"
            else -> ""
        }
        val res = online.call("GET", "/market?sort=$sort&page=$page$k", auth = false, device = false)
        val list = res.getJSONArray("items")
        MarketPage(List(list.length()) { parseItem(list.getJSONObject(it)) }.filter { it.item != null }, res.optInt("page"), res.optBoolean("more"))
    }

    suspend fun myMarket(): Net<MyMarket> = request {
        val res = online.call("GET", "/market/mine")
        val list = res.getJSONArray("items")
        MyMarket(List(list.length()) { parseItem(list.getJSONObject(it)) }, res.optLong("pending"))
    }

    suspend fun sell(item: ShopItem, name: String, price: Int): Net<MarketItem> = request {
        val body = JSONObject()
            .put("kind", item.kind.name.lowercase())
            .put("design", item.id)
            .put("name", name.trim())
            .put("price", price)
        parseItem(online.call("POST", "/market", body).getJSONObject("item"))
    }

    suspend fun unlist(id: Int): Net<Unit> = request {
        online.call("POST", "/market/$id/remove")
        Unit
    }

    /** Zakup: serwer zapisuje sprzedaż, a gra odejmuje monety i dodaje projekt (ponowiony zakup – bez drugiej zapłaty). */
    suspend fun buy(entry: MarketItem): Net<Boolean> = request {
        val item = entry.item ?: throw ApiException(0, "design_invalid", JSONObject())
        val res = online.call("POST", "/market/${entry.id}/buy", JSONObject().put("deviceId", online.deviceId), auth = online.loggedIn)
        val already = res.optBoolean("already")
        val ok = repo.progress.buyFromMarket(item, res.optInt("price", entry.price), charge = !already)
        repo.changed()
        ok
    }

    /**
     * Odbiór zarobku z targu. Identyfikator prośby trafia najpierw do zapisu gry – po zerwanym połączeniu
     * kolejna próba pyta o tę samą wypłatę i monety nie dublują się ani nie przepadają.
     */
    suspend fun claim(): Net<Int> = claimLock.withLock {
        request {
            if (repo.save.marketClaim.isEmpty()) {
                repo.save.marketClaim = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                repo.changed()
            }
            val res = online.call("POST", "/market/claim", JSONObject().put("claimId", repo.save.marketClaim))
            val amount = res.optInt("amount")
            if (amount > 0) repo.progress.addCoins(amount.toLong())
            repo.save.marketClaim = ""
            repo.changed()
            online.applyProfileOf(res)
            amount
        }
    }

    /** Odbiór przerwany wcześniej (np. gra zamknięta w trakcie) – dokańczamy przy następnym połączeniu. */
    suspend fun resumeClaim() {
        if (repo.save.marketClaim.isNotEmpty() && online.loggedIn) claim()
    }

    private fun parseItem(o: JSONObject): MarketItem {
        val seller = o.getJSONObject("seller")
        return MarketItem(
            id = o.getInt("id"),
            kind = if (o.getString("kind") == "sail") ItemKind.SAIL else ItemKind.BOAT,
            design = o.getString("design"),
            name = o.getString("name"),
            price = o.getInt("price"),
            sales = o.optInt("sales"),
            sellerId = seller.getLong("id"),
            seller = seller.getString("nick"),
            active = o.optBoolean("active", true),
            earned = o.optLong("earned"),
        )
    }
}
