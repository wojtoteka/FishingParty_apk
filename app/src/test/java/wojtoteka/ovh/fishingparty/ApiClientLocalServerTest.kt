package wojtoteka.ovh.fishingparty

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.data.ClockState
import wojtoteka.ovh.fishingparty.data.ClockStore
import wojtoteka.ovh.fishingparty.data.GameClock
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.Challenges
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import wojtoteka.ovh.fishingparty.online.ApiClient
import wojtoteka.ovh.fishingparty.online.ApiException
import wojtoteka.ovh.fishingparty.online.LaunchInfo
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

/**
 * Klient aplikacji przeciwko lokalnemu serwerowi Top_FishingParty
 * (`PORT=3811 HMAC_SECRET=local-test-secret ATTESTATION_MODE=off npm run dev` –
 * atestację telefonu da się sprawdzić tylko na prawdziwym urządzeniu). Bez serwera test jest pomijany.
 */
class ApiClientLocalServerTest {

    private fun serverUp(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 300) }
        true
    } catch (_: Exception) {
        false
    }

    @Test
    fun registerRunsSnapshotAndRanking() {
        assumeTrue("lokalny serwer nie działa", serverUp())
        val api = ApiClient("http://127.0.0.1:$PORT/api/v1", "local-test-secret", 10)
        val device = "c".repeat(32)
        val nick = "Jvm" + (System.currentTimeMillis() % 100000)

        val reg = api.call(
            "POST", "/auth/register",
            JSONObject().put("nick", nick).put("password", "haslo1234").put("deviceId", device)
                .put("acceptPolicy", true).put("policyVersion", 1),
        )
        val token = reg.getString("token")
        assertEquals(nick, reg.getJSONObject("account").getString("nick"))

        val now = System.currentTimeMillis()
        val run = JSONObject().put("level", 1).put("score", 400).put("timeMs", 15_000).put("common", 5).put("blue", 0)
            .put("golden", 0).put("hits", 0).put("maxCombo", 3).put("stages", 1).put("at", now - 1000)
        val integrity = JSONObject().put("tamperEvents", 0).put("memoryTampered", false).put("saveTampered", false).put("debuggable", true)
        val runs = api.call("POST", "/runs", JSONObject().put("runs", JSONArray().put(run)).put("trophies", 1).put("integrity", integrity), token)
        assertEquals(1, runs.getInt("accepted"))
        assertEquals(401L, runs.getJSONObject("account").getLong("points"))

        val blob = Base64.getEncoder().encodeToString("FPSD".toByteArray() + ByteArray(64) { it.toByte() })
        val put = api.call("PUT", "/sync/snapshot", JSONObject().put("baseRevision", 0).put("deviceId", device).put("data", blob), token)
        assertEquals(1, put.getInt("revision"))
        assertEquals(blob, api.call("GET", "/sync/snapshot", null, token).getString("data"))

        val ranking = api.call("GET", "/ranking", null, token)
        assertTrue(ranking.getJSONArray("entries").length() >= 1)
        assertTrue(!ranking.getJSONObject("me").isNull("rank"))

        // Ten sam nick + hasło na „nowym telefonie” = logowanie i przywrócenie zapisu.
        val login = api.call("POST", "/auth/login", JSONObject().put("login", nick.lowercase()).put("password", "haslo1234").put("deviceId", "d".repeat(32)))
        assertEquals(1, login.getJSONObject("snapshot").getInt("revision"))

        val wrong = runCatching {
            api.call("POST", "/auth/login", JSONObject().put("login", nick).put("password", "zlehaslo1").put("deviceId", device))
        }.exceptionOrNull() as ApiException
        assertEquals("invalid_credentials", wrong.code)

        assertTrue(api.call("POST", "/account/delete", JSONObject().put("password", "haslo1234"), token).getBoolean("deleted"))
    }

    /**
     * Konto „tylko nick” i poziomy dnia/tygodnia: seed z serwera, plansza liczona w grze,
     * wynik do rankingu (tydzień podwójnie, każdy okres raz), potem hasło i wylogowanie.
     */
    @Test
    fun nickOnlyAccountAndDailyLevels() {
        assumeTrue("lokalny serwer nie działa", serverUp())
        val clock = GameClock({ System.nanoTime() / 1_000_000 }, { System.currentTimeMillis() }, 1, object : ClockStore {
            private var saved: ClockState? = null
            override fun load() = saved
            override fun save(state: ClockState) {
                saved = state
            }
        })
        val api = ApiClient("http://127.0.0.1:$PORT/api/v1", "local-test-secret", 12, clock = clock)
        val device = "e".repeat(32)
        val nick = "Nick" + (System.currentTimeMillis() % 100000)

        val joined = api.call(
            "POST", "/auth/nick",
            JSONObject().put("nick", nick).put("deviceId", device).put("acceptPolicy", true).put("policyVersion", 1),
        )
        val token = joined.getString("token")
        assertEquals(false, joined.getJSONObject("account").getBoolean("hasPassword"))

        // Seed z serwera, plansza i limity liczone w grze.
        val json = api.challenges()!!
        assertTrue(clock.synced)
        val save = SaveData()
        val progress = Progress(save, clock)
        val challenges = ChallengeKind.entries.map { kind ->
            val o = json.getJSONObject(kind.id)
            Challenge(kind, o.getInt("period"), LevelSeed(o.getString("seed").toLong()), o.getLong("startsAt"), o.getLong("endsAt"))
        }
        progress.cacheChallenges(challenges)
        val integrity = JSONObject().put("tamperEvents", 0).put("memoryTampered", false).put("saveTampered", false).put("debuggable", true)
        var expected = 0L
        for (challenge in challenges) {
            assertEquals(challenge, progress.currentChallenge(challenge.kind))
            val plan = challenge.plan()
            val result = RunResult(
                level = Challenges.LEVEL_NUMBER, completed = true, score = LevelGenerator.maxScore(plan) / 3, stars = 1,
                starFlags = booleanArrayOf(true, false, false), timeMillis = (ScoreValidator.minimumSeconds(plan) * 4000).toLong(),
                commonCaught = plan.stages.sumOf { it.common }, blueCaught = 0, goldenCaught = 0, hits = 1, maxCombo = 3,
                stagesCleared = plan.stages.size,
            )
            val reward = progress.applyChallenge(result, plan, challenge)
            assertTrue(reward.accepted)
            // Dokładnie to, co wysyła OnlineManager.pushChallenges.
            val r = save.challengeResults.last()
            val body = JSONObject().put("kind", r.kind.id).put("period", r.period).put(
                "run",
                JSONObject().put("score", r.score).put("timeMs", r.timeMillis).put("common", r.common).put("blue", r.blue)
                    .put("golden", r.golden).put("hits", r.hits).put("maxCombo", r.maxCombo).put("stages", r.stages).put("at", r.at),
            ).put("integrity", integrity)
            val res = api.call("POST", "/challenges/run", body, token)
            assertTrue(res.toString(), res.getBoolean("accepted"))
            assertEquals(reward.challengePoints, res.getInt("points"))
            expected += reward.challengePoints
            assertEquals(expected, res.getJSONObject("account").getLong("points"))
            // Ten sam okres drugi raz – bez punktów.
            assertTrue(api.call("POST", "/challenges/run", body, token).getBoolean("duplicate"))
        }
        // Lokalnie dochodzi jeszcze trofeum za dzień passy (ukończony poziom dnia też ją przedłuża).
        assertEquals(expected + save.trophies.get(), progress.rankingPoints())
        assertTrue(save.trophies.get() > 0)
        assertTrue(api.call("GET", "/ranking").getJSONArray("entries").let { e -> (0 until e.length()).any { e.getJSONObject(it).getString("nick") == nick } })

        // E-mail dopiero po haśle.
        val noPassword = runCatching { api.call("POST", "/account/email", JSONObject().put("email", "a@b.pl").put("password", "x"), token) }
        assertEquals("password_required", (noPassword.exceptionOrNull() as ApiException).code)
        assertTrue(api.call("POST", "/account/set-password", JSONObject().put("password", "haslo1234"), token).getJSONObject("account").getBoolean("hasPassword"))
        val login = api.call("POST", "/auth/login", JSONObject().put("login", nick).put("password", "haslo1234").put("deviceId", device))
        assertTrue(api.call("POST", "/account/delete", JSONObject().put("password", "haslo1234"), login.getString("token")).getBoolean("deleted"))
    }

    /**
     * Start gry: `GET /launch` bez podpisu i bez konta – ta wersja nie jest zablokowana, a ogłoszenia
     * da się odczytać (dodaje je admin w panelu). Blokada wersji z panelu jest sprawdzana w testach serwera.
     */
    @Test
    fun launchCheck() {
        assumeTrue("lokalny serwer nie działa", serverUp())
        val clock = GameClock({ System.nanoTime() / 1_000_000 }, { System.currentTimeMillis() }, 1, object : ClockStore {
            private var saved: ClockState? = null
            override fun load() = saved
            override fun save(state: ClockState) {
                saved = state
            }
        })
        // Bez sekretu HMAC (gra zbudowana bez local.properties) – /launch i tak działa.
        val api = ApiClient("http://127.0.0.1:$PORT/api/v1", "", BuildConfig.VERSION_CODE, clock = clock)
        for (account in listOf(false, true)) {
            val info = LaunchInfo.parse(api.launch(account)!!)
            assertNull(info.block)
            for (a in info.announcements) assertTrue(a.title(true).isNotEmpty() && a.body(false).isNotEmpty())
        }
        assertTrue(clock.synced)
        // Wersja poniżej MIN_APP_VERSION serwera – zablokowana.
        val old = ApiClient("http://127.0.0.1:$PORT/api/v1", "", 1, clock = clock)
        assertTrue(LaunchInfo.parse(old.launch(false)!!).block != null)
    }

    companion object {
        private const val PORT = 3811
    }
}
