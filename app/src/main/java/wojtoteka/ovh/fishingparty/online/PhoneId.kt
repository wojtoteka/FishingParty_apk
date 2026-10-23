package wojtoteka.ovh.fishingparty.online

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stały identyfikator telefonu do gwiazdek poziomów graczy: jedna gwiazdka na telefon także po ponownej
 * instalacji gry (identyfikator instalacji [AccountStore.deviceId] wtedy się zmienia). Android podaje go osobno
 * dla każdej aplikacji (Settings.Secure.ANDROID_ID), a do serwera trafia tylko jego skrót – serwer i tak skraca go
 * jeszcze raz ze swoją solą. Nie służy do reklam ani śledzenia.
 */
object PhoneId {
    @SuppressLint("HardwareIds")
    fun of(context: Context): String {
        val id = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
        return if (id.isNullOrBlank()) "" else hash(id)
    }

    fun hash(androidId: String): String =
        MessageDigest.getInstance("SHA-256").digest("fishingparty-phone:$androidId".toByteArray()).joinToString("") { "%02x".format(it) }
}
