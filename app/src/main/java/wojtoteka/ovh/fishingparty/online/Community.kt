package wojtoteka.ovh.fishingparty.online

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.BuildConfig
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
    /** Gwiazdki od graczy (jedna na telefon i jedna na konto) – im więcej, tym wyżej w zakładce NAJLEPSZE. */
    val stars: Int,
    val featured: Boolean,
    val hidden: Boolean = false,
    val hiddenReason: String? = null,
) {
    /** Jaka część graczy ukończyła poziom (w procentach), null – nikt jeszcze nie grał. */
    val completionRate: Int? get() = if (players > 0) completions * 100 / players else null
}

class LevelPage(val levels: List<OnlineLevel>, val page: Int, val more: Boolean)

/** Stan tego telefonu na poziomie: próby, ukończenie, rekord, gwiazdka. [creator] – to poziom tego gracza. */
class PlayerState(val attempts: Int, val completed: Boolean, val best: Int, val starred: Boolean, val reported: Boolean, val creator: Boolean) {
    /** Gwiazdkę daje się po zagraniu w poziom (na tym telefonie) i nie własnemu poziomowi. */
    val canStar: Boolean get() = attempts > 0 && !creator

    fun withStar(starred: Boolean) = PlayerState(attempts, completed, best, starred, reported, creator)
}

class LevelDetails(val level: OnlineLevel, val data: UserLevel, val me: PlayerState?)

/** Gwiazdka po odpowiedzi serwera: aktualny poziom (liczba gwiazdek) i czy ten gracz ją dał. */
class StarResult(val level: OnlineLevel, val starred: Boolean)

/** Poziom z linku bez publikacji (/s/<kod>): do zagrania bez punktów i monet. [creator] – nick, gdy udostępniono z konta. */
class SharedLevel(val code: String, val title: String, val creator: String?, val data: UserLevel)

/**
 * Odpowiedź serwera na przejazd: monety za pierwsze ukończenie (już dodane do zapisu), punkty rankingu
 * za ten poziom (0 – poziom bez oceny albo ponad dzienny limit) i aktualne liczby poziomu.
 */
class PlayOutcome(
    val firstCompletion: Boolean,
    val coins: Int,
    val points: Int,
    val overDailyLimit: Boolean,
    /** Przejazd autora poziomu – bez monet i punktów. */
    val own: Boolean,
    val level: OnlineLevel,
)

/**
 * Projekt na targu: [design] to id rzeczy z kreatora (np. „own-1E90FF”). [stars] – gwiazdki od graczy
 * (jak przy poziomach), [starred] – ten telefon dał gwiazdkę.
 */
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
    val stars: Int = 0,
    val starred: Boolean = false,
) {
    /** Punkty projektu (zakładka NAJLEPSZE): sprzedaże + gwiazdki. */
    val points: Int get() = sales + stars

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

    /** Link do poziomu, którym otwarto grę – czeka, aż gracz będzie poza rozgrywką (AppRoot). */
    var pendingLink by mutableStateOf<LevelLink?>(null)

    /** Link z intencji (App Links albo przycisk ZAGRAJ na stronie poziomu); false – to nie link do poziomu. */
    fun openLink(link: String?): Boolean {
        val parsed = LevelLinks.parse(link, BuildConfig.FP_LINK_HOST, BuildConfig.FP_LINK_PREFIX) ?: return false
        pendingLink = parsed
        return true
    }

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
        val phone = online.phoneId.takeIf { it.isNotEmpty() }?.let { "&phone=$it" }.orEmpty()
        val res = online.call("GET", "/levels/$id?device=${online.deviceId}$phone", auth = online.loggedIn)
        val o = res.getJSONObject("level")
        val data = UserLevels.decode(o.getString("data")) ?: throw ApiException(0, "level_format", JSONObject())
        val me = res.optJSONObject("me")?.let {
            PlayerState(it.optInt("attempts"), it.optBoolean("completed"), it.optInt("best"), it.optBoolean("starred"), it.optBoolean("reported"), it.optBoolean("creator"))
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

    /** Autor zmienia nazwę ([title]) albo zgodę na kopiowanie ([copyable]) – null zostawia bez zmian. */
    suspend fun updateLevel(id: Int, title: String?, copyable: Boolean?): Net<OnlineLevel> = request {
        val body = JSONObject()
        title?.let { body.put("title", it) }
        copyable?.let { body.put("copyable", it) }
        parseLevel(online.call("POST", "/levels/$id/settings", body).getJSONObject("level"))
    }

    suspend fun delete(id: Int): Net<Unit> = request {
        online.applyProfileOf(online.call("POST", "/levels/$id/delete"))
    }

    /** Gwiazdka dla poziomu ([star] = true) albo jej cofnięcie. Jedna na telefon i jedna na konto (pilnuje serwer). */
    suspend fun star(id: Int, star: Boolean): Net<StarResult> = request {
        val body = JSONObject().put("deviceId", online.deviceId).put("star", star)
        online.phoneId.takeIf { it.isNotEmpty() }?.let { body.put("phone", it) }
        val res = online.call("POST", "/levels/$id/star", body, auth = online.loggedIn)
        StarResult(parseLevel(res.getJSONObject("level")), res.optBoolean("starred", star))
    }

    /** Link do opublikowanego poziomu ([lang] – język gry: podgląd linku w komunikatorze w tym języku). */
    suspend fun shareLink(id: Int, lang: String): Net<String> = request {
        val body = JSONObject().put("deviceId", online.deviceId).put("levelId", id).put("lang", lang)
        online.call("POST", "/shares", body, auth = online.loggedIn).getString("url")
    }

    /** Link do poziomu z kreatora bez publikacji – bez przejścia poziomu, konta i innych wymagań publikacji. */
    suspend fun shareDraft(level: UserLevel, lang: String): Net<String> = request {
        val body = JSONObject().put("deviceId", online.deviceId).put("data", UserLevels.encode(level)).put("lang", lang)
        online.call("POST", "/shares", body, auth = online.loggedIn).getString("url")
    }

    /** Poziom z linku /s/<kod>. */
    suspend fun shared(code: String): Net<SharedLevel> = request {
        val res = online.call("GET", "/shares/$code", auth = false, device = false)
        val share = res.getJSONObject("share")
        val data = UserLevels.decode(res.getString("data")) ?: throw ApiException(0, "level_format", JSONObject())
        val creator = share.optJSONObject("creator")?.optString("nick")?.takeIf { it.isNotEmpty() }
        SharedLevel(share.getString("code"), share.optString("title").ifBlank { data.title }, creator, data)
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
                            res.optBoolean("own"),
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
            stars = o.optInt("stars"),
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
        // Telefon (device, phone) – serwer zaznacza projekty, którym ten telefon dał gwiazdkę.
        val phone = online.phoneId.takeIf { it.isNotEmpty() }?.let { "&phone=$it" }.orEmpty()
        val res = online.call("GET", "/market?sort=$sort&page=$page$k&device=${online.deviceId}$phone", auth = false, device = false)
        val list = res.getJSONArray("items")
        MarketPage(List(list.length()) { parseItem(list.getJSONObject(it)) }.filter { it.item != null }, res.optInt("page"), res.optBoolean("more"))
    }

    /** Gwiazdka dla projektu ([star] = true) albo jej cofnięcie. Jedna na telefon i jedna na konto (pilnuje serwer). */
    suspend fun starDesign(id: Int, star: Boolean): Net<MarketItem> = request {
        val body = JSONObject().put("deviceId", online.deviceId).put("star", star)
        online.phoneId.takeIf { it.isNotEmpty() }?.let { body.put("phone", it) }
        val res = online.call("POST", "/market/$id/star", body, auth = online.loggedIn)
        parseItem(res.getJSONObject("item"), starred = res.optBoolean("starred", star))
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

    private fun parseItem(o: JSONObject, starred: Boolean = o.optBoolean("starred")): MarketItem {
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
            stars = o.optInt("stars"),
            starred = starred,
        )
    }
}
