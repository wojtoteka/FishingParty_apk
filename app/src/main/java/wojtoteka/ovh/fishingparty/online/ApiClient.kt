package wojtoteka.ovh.fishingparty.online

import org.json.JSONObject
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
 * bez podpisu, sprzed ponad 5 minut i powtórzone (ten sam nonce).
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

/** Klient REST serwera Top_FishingParty. Wywołania blokujące – tylko poza wątkiem UI. */
class ApiClient(
    private val baseUrl: String,
    private val secret: String,
    private val appVersion: Int,
) {
    // Ruch bez szyfrowania blokuje sam Android (targetSdk 28+), więc w praktyce zawsze https.
    val configured: Boolean get() = secret.isNotEmpty() && baseUrl.isNotEmpty()

    fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        if (!configured) throw ApiException(0, "not_configured", JSONObject())
        val url = URL(baseUrl + path)
        val raw = body?.toString()?.toByteArray() ?: ByteArray(0)
        val time = System.currentTimeMillis()
        val nonce = ApiSigning.nonce()
        val signature = ApiSigning.sign(secret, ApiSigning.message(method, url.file, time, nonce, ApiSigning.sha256Hex(raw)))
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
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(raw) }
            }
            val status = conn.responseCode
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
