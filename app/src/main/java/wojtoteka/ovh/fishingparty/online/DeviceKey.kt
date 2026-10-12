package wojtoteka.ovh.fishingparty.online

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Klucz tej instalacji gry w sprzętowym magazynie telefonu (Android Keystore).
 *
 * Przy tworzeniu klucza telefon wystawia łańcuch certyfikatów zakończony certyfikatem Google
 * („atestacja klucza”). Jest w nim m.in. skrót certyfikatu, którym podpisano zainstalowaną
 * aplikację – serwer sprawdza go i przyjmuje tylko oficjalne wydania gry. Klucz prywatny nigdy
 * nie opuszcza telefonu; każde żądanie do serwera jest nim podpisane.
 */
object DeviceKey {
    private const val ALIAS = "fp_device_v1"
    private const val PROVIDER = "AndroidKeyStore"

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun exists(): Boolean = try {
        keyStore().containsAlias(ALIAS)
    } catch (_: Exception) {
        false
    }

    fun delete() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }

    /** Nowy klucz z wyzwaniem od serwera. Zwraca łańcuch certyfikatów (DER, base64) – od klucza do Google. */
    fun create(challenge: ByteArray): List<String> {
        delete()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply { initialize(spec) }.generateKeyPair()
        val chain = keyStore().getCertificateChain(ALIAS) ?: throw IllegalStateException("brak łańcucha atestacji")
        return chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }
    }

    /** Podpis ECDSA/SHA-256 (DER, base64) albo null, gdy klucza jeszcze nie ma. */
    fun sign(message: String): String? = try {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey
        key?.let {
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(it)
            s.update(message.toByteArray())
            Base64.encodeToString(s.sign(), Base64.NO_WRAP)
        }
    } catch (_: Exception) {
        null
    }
}
