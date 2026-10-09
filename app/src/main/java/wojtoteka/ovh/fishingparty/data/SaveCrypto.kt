package wojtoteka.ovh.fishingparty.data

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface KeyProvider {
    fun key(): SecretKey
}

/**
 * Szyfrowanie zapisu AES-256-GCM. GCM to szyfrowanie z uwierzytelnieniem:
 * zmiana choćby jednego bajtu pliku (np. podbicie monet edytorem) sprawia,
 * że odczyt się nie powiedzie. Na urządzeniu klucz leży w Android Keystore
 * (sprzętowo, bez możliwości wyciągnięcia), więc pliku nie da się też
 * przenieść na inny telefon ani odszyfrować poza aplikacją.
 */
class SaveCrypto(private val keys: KeyProvider) {

    fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        cipher.updateAAD(HEADER)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return ByteBuffer.allocate(HEADER.size + 1 + iv.size + body.size)
            .put(HEADER)
            .put(iv.size.toByte())
            .put(iv)
            .put(body)
            .array()
    }

    /** @throws GeneralSecurityException gdy plik został zmieniony lub pochodzi z innego urządzenia */
    fun open(sealed: ByteArray): ByteArray {
        if (sealed.size < HEADER.size + 1 + 12 + 16) throw GeneralSecurityException("Za krótki zapis")
        for (i in HEADER.indices) if (sealed[i] != HEADER[i]) throw GeneralSecurityException("Zły nagłówek")
        val ivLength = sealed[HEADER.size].toInt()
        if (ivLength !in 12..16) throw GeneralSecurityException("Zły IV")
        val ivStart = HEADER.size + 1
        val iv = sealed.copyOfRange(ivStart, ivStart + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(128, iv))
        cipher.updateAAD(HEADER)
        return cipher.doFinal(sealed, ivStart + ivLength, sealed.size - ivStart - ivLength)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val HEADER = byteArrayOf('F'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte(), 1)
    }
}
