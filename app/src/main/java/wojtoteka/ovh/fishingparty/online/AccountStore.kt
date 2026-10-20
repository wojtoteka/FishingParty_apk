package wojtoteka.ovh.fishingparty.online

import android.util.Log
import wojtoteka.ovh.fishingparty.data.SaveCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Sesja konta online na tym urządzeniu – osobny, zaszyfrowany plik (token
 * nie może trafić do zapisu gry wysyłanego na serwer).
 */
class AccountStore(dir: File, private val crypto: SaveCrypto) {
    private val file = File(dir, "account.fps")
    private val io = Executors.newSingleThreadExecutor()

    /** Losowy identyfikator instalacji – serwer odróżnia nim zapisy z różnych telefonów. */
    var deviceId: String = ""
        private set
    var token: String? = null
    var accountId: Long = 0
    var nick: String = ""
    var email: String? = null

    /** Rewizja zapisu na serwerze, od której pochodzi stan tego telefonu. */
    var knownRevision: Int = 0

    /** Czas (ScoreRecord.at) ostatniego przejazdu wysłanego na serwer. */
    var lastSentRunAt: Long = 0

    /** Czas (ChallengeResult.at) ostatniego poziomu dnia/tygodnia wysłanego na serwer. */
    var lastSentChallengeAt: Long = 0

    /** Konto „tylko nick” nie ma hasła – wylogowanie oznacza utratę nicku. */
    var hasPassword: Boolean = true

    /** Postęp sprzed założenia konta czeka na jednorazowy import. */
    var importPending: Boolean = false

    /** Zapis zmienił się od ostatniej udanej wysyłki. */
    var dirty: Boolean = false

    /** Klucz telefonu ([DeviceKey]) przeszedł atestację na serwerze. Nie zależy od konta – zostaje po wylogowaniu. */
    var deviceAttested: Boolean = false

    val loggedIn: Boolean get() = token != null

    init {
        load()
        if (deviceId.length != 32) {
            deviceId = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            save()
        }
    }

    fun clearSession() {
        token = null
        accountId = 0
        nick = ""
        email = null
        knownRevision = 0
        lastSentRunAt = 0
        lastSentChallengeAt = 0
        hasPassword = true
        importPending = false
        dirty = false
        save()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            DataInputStream(ByteArrayInputStream(crypto.open(file.readBytes()))).use { i ->
                require(i.readInt() == MAGIC)
                val version = i.readShort().toInt()
                deviceId = i.readUTF()
                token = i.readUTF().ifEmpty { null }
                accountId = i.readLong()
                nick = i.readUTF()
                email = i.readUTF().ifEmpty { null }
                knownRevision = i.readInt()
                lastSentRunAt = i.readLong()
                importPending = i.readBoolean()
                dirty = i.readBoolean()
                if (version >= 2) deviceAttested = i.readBoolean()
                if (version >= 3) {
                    lastSentChallengeAt = i.readLong()
                    hasPassword = i.readBoolean()
                }
            }
        } catch (e: Exception) {
            Log.w("FishingParty", "Nie można odczytać sesji konta", e)
        }
    }

    fun save() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { o ->
            o.writeInt(MAGIC)
            o.writeShort(3)
            o.writeUTF(deviceId)
            o.writeUTF(token.orEmpty())
            o.writeLong(accountId)
            o.writeUTF(nick)
            o.writeUTF(email.orEmpty())
            o.writeInt(knownRevision)
            o.writeLong(lastSentRunAt)
            o.writeBoolean(importPending)
            o.writeBoolean(dirty)
            o.writeBoolean(deviceAttested)
            o.writeLong(lastSentChallengeAt)
            o.writeBoolean(hasPassword)
        }
        val plain = bytes.toByteArray()
        io.execute {
            try {
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(crypto.seal(plain))
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.e("FishingParty", "Zapis sesji konta nieudany", e)
            }
        }
    }

    companion object {
        private const val MAGIC = 0x46504143 // "FPAC"
    }
}
