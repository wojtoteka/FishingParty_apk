package wojtoteka.ovh.fishingparty

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.online.ApiClient
import wojtoteka.ovh.fishingparty.online.ApiException
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

    companion object {
        private const val PORT = 3811
    }
}
