package wojtoteka.ovh.fishingparty.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

/**
 * Klucz zapisu z Android Keystore. Gdyby Keystore na danym urządzeniu nie
 * działał, używamy klucza losowego w prywatnym katalogu bez kopii zapasowej.
 */
internal class KeystoreKeys(private val context: Context) : KeyProvider {
    private var cached: SecretKey? = null

    override fun key(): SecretKey = cached ?: create().also { cached = it }

    private fun create(): SecretKey = try {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    } catch (e: Exception) {
        Log.w("FishingParty", "Keystore niedostępny, używam klucza zapasowego", e)
        val file = File(context.noBackupFilesDir, ".fpk")
        val bytes = if (file.length() == 32L) file.readBytes() else ByteArray(32).also {
            SecureRandom().nextBytes(it)
            file.writeBytes(it)
        }
        SecretKeySpec(bytes, "AES")
    }

    companion object {
        private const val ALIAS = "fishingparty.save.v1"
    }
}

/** Stan [GameClock] w pliku poza kopią zapasową – dotyczy tylko tego telefonu i tego jego uruchomienia. */
internal class ClockFile(private val file: File) : ClockStore {
    override fun load(): ClockState? = try {
        if (!file.exists()) {
            null
        } else {
            val v = file.readLines().associate { it.substringBefore('=') to it.substringAfter('=', "") }
            ClockState(
                boot = v["boot"]?.toLongOrNull() ?: -1,
                serverMs = v["server"]?.toLongOrNull() ?: 0,
                elapsedMs = v["elapsed"]?.toLongOrNull() ?: 0,
                zone = v["zone"]?.takeIf { it.isNotEmpty() } ?: GameClock.DEFAULT_ZONE,
                pending = v["pending"].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            )
        }
    } catch (e: Exception) {
        Log.w("FishingParty", "Nie można odczytać stanu zegara", e)
        null
    }

    override fun save(state: ClockState) {
        try {
            file.writeText(
                listOf(
                    "boot=${state.boot}",
                    "server=${state.serverMs}",
                    "elapsed=${state.elapsedMs}",
                    "zone=${state.zone}",
                    "pending=${state.pending.joinToString(",")}",
                ).joinToString("\n"),
            )
        } catch (e: Exception) {
            Log.w("FishingParty", "Nie można zapisać stanu zegara", e)
        }
    }
}

/** Trwały stan gry: wczytanie, zapis (atomowy, z kopią) i powiadamianie UI o zmianach. */
class GameRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "progress.fps")
    private val backup = File(appContext.filesDir, "progress.bak")
    internal val crypto = SaveCrypto(KeystoreKeys(appContext))
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Zegar gry – godzina z serwera, nie z ustawień telefonu. */
    val clock = GameClock(
        elapsed = SystemClock::elapsedRealtime,
        wall = System::currentTimeMillis,
        boot = Settings.Global.getLong(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1L),
        store = ClockFile(File(appContext.noBackupFilesDir, "clock.txt")),
    )

    var save: SaveData
        private set
    var progress: Progress
        private set

    /** Wywoływane po każdej zmianie stanu (synchronizacja z kontem online). */
    var onChanged: (() -> Unit)? = null

    /** Licznik zmian – Compose czyta go, żeby odświeżać widoki po każdej zmianie postępu. */
    val revision = mutableIntStateOf(0)

    /** Osiągnięcia czekające na pokazanie baneru. */
    val banners = mutableStateListOf<Achievement>()

    /** Ustawione, gdy plik zapisu był zmieniony z zewnątrz i nie dało się go odczytać. */
    var saveWasTampered = false
        private set

    init {
        save = load()
        progress = progressOf(save)
        migrateLegacy()
        progress.touchClock()
        // Osiągnięcia zdobyte wcześniej (np. weteran) nie pokazują baneru przy starcie.
        progress.takeNewAchievements()
        persist()
        clock.onSynced = { main.post(::onClockSynced) }
    }

    private fun progressOf(data: SaveData) = Progress(data, clock)

    /** Zegar poznał czas serwera: zaliczamy passę za poziomy ukończone bez niego i odświeżamy widoki. */
    private fun onClockSynced() {
        var credited = false
        for (day in clock.takeDeferredDays()) if (progress.creditStreakDay(day) > 0) credited = true
        if (credited) changed() else revision.intValue++
    }

    /** Zapis gry w formacie [SaveCodec] – wysyłany na konto online. */
    fun exportSnapshot(): ByteArray = SaveCodec.encode(save)

    /**
     * Zastępuje cały postęp zapisem pobranym z konta (np. po reinstalacji).
     * @throws Exception gdy dane są uszkodzone – wtedy lokalny zapis zostaje bez zmian
     */
    fun restoreSnapshot(bytes: ByteArray) {
        val restored = SaveCodec.decode(bytes)
        // Sterowanie wybrane na tym telefonie zostaje – gracz wybrał je przed zalogowaniem.
        if (save.controlsChosen) {
            restored.controls = save.controls
            restored.controlsChosen = true
            restored.joystickLeft = save.joystickLeft
        }
        save = restored
        progress = progressOf(restored)
        progress.touchClock()
        // Odznaki z konta były już pokazane na tamtym urządzeniu.
        progress.takeNewAchievements()
        banners.clear()
        revision.intValue++
        persist()
    }

    private fun load(): SaveData {
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            try {
                return SaveCodec.decode(crypto.open(candidate.readBytes()))
            } catch (e: Exception) {
                Log.w("FishingParty", "Nie można odczytać ${candidate.name}", e)
                saveWasTampered = true
            }
        }
        return SaveData()
    }

    /** Poprzednia wersja (WebView) trzymała łączną liczbę ryb – weterani dostają za nią monety. */
    private fun migrateLegacy() {
        if (save.legacyMigrated) return
        save.legacyMigrated = true
        val prefs = appContext.getSharedPreferences("fishingparty_prefs", Context.MODE_PRIVATE)
        val totalFish = prefs.getInt("total_fish", 0)
        val highScore = prefs.getInt("high_score", 0)
        if (totalFish > 0 || highScore > 0) {
            save.veteran = true
            save.tutorialDone = true
            progress.addCoins(min(totalFish, 2000).toLong())
            prefs.edit().clear().apply()
        }
    }

    /** Wywołuj po każdej zmianie stanu. */
    fun changed() {
        progress.touchClock()
        banners += progress.takeNewAchievements()
        revision.intValue++
        persist()
        onChanged?.invoke()
    }

    private fun persist() {
        val plain = SaveCodec.encode(save)
        io.execute {
            try {
                val sealed = crypto.seal(plain)
                val tmp = File(file.path + ".tmp")
                tmp.writeBytes(sealed)
                if (file.exists()) {
                    backup.delete()
                    file.renameTo(backup)
                }
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.e("FishingParty", "Zapis nieudany", e)
            }
        }
    }
}
