package wojtoteka.ovh.fishingparty.online

import org.json.JSONObject
import wojtoteka.ovh.fishingparty.data.GameClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Podpis żądań do serwera rankingu (HMAC-SHA256 wspólnym sekretem aplikacji):
 * METODA \n ŚCIEŻKA \n CZAS \n NONCE \n sha256(treść). Serwer odrzuca żądania
 * bez podpisu, sprzed ponad 5 minut i powtórzone (ten sam nonce). Ta sama treść
 * jest dodatkowo podpisana kluczem telefonu ([DeviceKey]) – sam sekret wyciągnięty
 * z aplikacji nie wystarczy, żeby wysyłać wyniki.
 */
object ApiSigning {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun message(method: String, path: String, time: Long, nonce: String, bodyHash: String): String =
        listOf(method.uppercase(), path, time.toString(), nonce, bodyHash).joinToString("\n")

    fun sign(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(message.toByteArray()).toHex()
    }

    fun nonce(): String = ByteArray(16).also { random.nextBytes(it) }.toHex()

    private val random = SecureRandom()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** Odpowiedź serwera z kodem błędu (np. `nick_taken`, `invalid_credentials`). */
class ApiException(val status: Int, val code: String, val body: JSONObject) : Exception(code)

/** Brak połączenia albo serwer nie odpowiada. */
class OfflineException(cause: Throwable? = null) : Exception("offline", cause)

/** Podpis żądań kluczem tego telefonu (DeviceKey) – serwer zna go z atestacji. */
interface DeviceSigner {
    val deviceId: String

    /** Podpis treści żądania albo null, gdy telefon nie ma jeszcze klucza. */
    fun sign(message: String): String?
}

/**
 * Klient REST serwera Top_FishingParty. Wywołania blokujące – tylko poza wątkiem UI.
 * Każda odpowiedź serwera niesie jego czas (`X-FP-Server-Time`) – trafia do [clock].
 */
class ApiClient(
    private val baseUrl: String,
    private val secret: String,
    private val appVersion: Int,
    private val device: DeviceSigner? = null,
    private val clock: GameClock? = null,
) {
    // Ruch bez szyfrowania blokuje sam Android (targetSdk 28+), więc w praktyce zawsze https.
    val configured: Boolean get() = secret.isNotEmpty() && baseUrl.isNotEmpty()

    /** Pobiera czas gry z serwera (`GET /time`, bez podpisu – działa też przy złej dacie w telefonie). */
    fun syncTime() {
        val clock = clock ?: return
        if (baseUrl.isEmpty()) return
        val time = getPublic("/time")
        if (time != null) {
            clock.onServerTime(time.json.optLong("now"), time.sentAt, time.receivedAt, time.json.optString("zone").takeIf { it.isNotEmpty() })
            return
        }
        // Serwer sprzed wersji z /time – ten sam czas jest w /health (strefa zostaje domyślna).
        val health = getPublic("/health") ?: return
        clock.onServerTime(health.json.optLong("time"), health.sentAt, health.receivedAt)
    }

    private class Timed(val json: JSONObject, val sentAt: Long, val receivedAt: Long)

    /** GET bez podpisu; null, gdy serwer nie zna ścieżki albo odpowiedział czymś innym niż JSON. */
    private fun getPublic(path: String): Timed? {
        val clock = clock ?: return null
        val conn = try {
            URL(baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw OfflineException(e)
        }
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            val sentAt = clock.elapsedNow()
            val status = conn.responseCode
            val receivedAt = clock.elapsedNow()
            if (status !in 200..299) return null
            return Timed(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }), sentAt, receivedAt)
        } catch (e: IOException) {
            throw OfflineException(e)
        } catch (_: org.json.JSONException) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun readServerTime(conn: HttpURLConnection, sentAt: Long, receivedAt: Long) {
        val clock = clock ?: return
        val server = conn.getHeaderField("X-FP-Server-Time")?.toLongOrNull() ?: return
        clock.onServerTime(server, sentAt, receivedAt)
    }

    fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject = try {
        request(method, path, body, token)
    } catch (e: ApiException) {
        // Zegar telefonu był przestawiony, a czasu serwera jeszcze nie znaliśmy – teraz już tak (nagłówek odpowiedzi).
        if (e.code != "clock_skew" || clock?.synced != true) throw e
        request(method, path, body, token)
    }

    private fun request(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        if (!configured) throw ApiException(0, "not_configured", JSONObject())
        val url = URL(baseUrl + path)
        val raw = body?.toString()?.toByteArray() ?: ByteArray(0)
        val time = clock?.now() ?: System.currentTimeMillis()
        val nonce = ApiSigning.nonce()
        val message = ApiSigning.message(method, url.file, time, nonce, ApiSigning.sha256Hex(raw))
        val signature = ApiSigning.sign(secret, message)
        val deviceSignature = device?.sign(message)
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw OfflineException(e)
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8_000
            conn.readTimeout = 15_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-FP-Time", time.toString())
            conn.setRequestProperty("X-FP-Nonce", nonce)
            conn.setRequestProperty("X-FP-Signature", signature)
            conn.setRequestProperty("X-FP-App", appVersion.toString())
            if (device != null && deviceSignature != null) {
                conn.setRequestProperty("X-FP-Device", device.deviceId)
                conn.setRequestProperty("X-FP-Device-Sig", deviceSignature)
            }
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(raw) }
            }
            val sentAt = clock?.elapsedNow() ?: 0L
            val status = conn.responseCode
            if (clock != null) readServerTime(conn, sentAt, clock.elapsedNow())
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: Exception) {
                // Np. strona błędu nginx, gdy serwer gry nie działa.
                throw if (status in 200..299) ApiException(status, "bad_response", JSONObject()) else OfflineException(e)
            }
            if (status !in 200..299) throw ApiException(status, json.optString("error", "server_error"), json)
            return json
        } catch (e: IOException) {
            throw OfflineException(e)
        } finally {
            conn.disconnect()
        }
    }
}
