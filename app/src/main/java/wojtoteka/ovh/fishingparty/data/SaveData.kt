package wojtoteka.ovh.fishingparty.data

import wojtoteka.ovh.fishingparty.core.GuardedIntArray
import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.notify.ChallengeReminderPlan
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Zweryfikowany przejazd poziomu – przygotowane pod przyszły ranking online
 * (serwer może odtworzyć poziom z numeru i sprawdzić te same limity).
 */
class ScoreRecord(
    val level: Int,
    val score: Int,
    val timeMillis: Long,
    val common: Int,
    val blue: Int,
    val golden: Int,
    val hits: Int,
    val maxCombo: Int,
    val stages: Int,
    val at: Long,
)

/** Ukończony poziom dnia/tygodnia ([period] – numer dnia albo tygodnia z serwera). */
class ChallengeResult(
    val kind: ChallengeKind,
    val period: Int,
    val score: Int,
    /** Punkty rankingowe (wynik × mnożnik rodzaju). */
    val points: Int,
    val timeMillis: Long,
    val common: Int,
    val blue: Int,
    val golden: Int,
    val hits: Int,
    val maxCombo: Int,
    val stages: Int,
    val at: Long,
)

/**
 * Zakończony przejazd poziomu gracza (z kreatora), który czeka na wysłanie na serwer – wygrany ([completed])
 * albo przegrany (liczy się do statystyk poziomu). Serwer odpowiada monetami i punktami rankingu.
 */
class UserLevelPlay(
    val levelId: Int,
    val completed: Boolean,
    val score: Int,
    val timeMillis: Long,
    val common: Int,
    val blue: Int,
    val golden: Int,
    val hits: Int,
    val maxCombo: Int,
    val stages: Int,
    val at: Long,
)

/** Sterowanie łodzią: przesuwanie palcem po całym ekranie albo stały joystick w rogu. */
enum class ControlMode { SWIPE, JOYSTICK }

/** Cały stan gracza. Najważniejsze liczby trzymane są w pamięci w postaci zamaskowanej. */
class SaveData {
    val coins = GuardedLong(0)
    /** Rekordy i gwiazdki poziomów (indeks = numer - 1). Rosną razem z Bezkresnym Morzem – patrz [ensureLevels]. */
    val best = GuardedIntArray(LevelConstants.LEVEL_COUNT)
    var stars = ByteArray(LevelConstants.LEVEL_COUNT)
        private set
    val unlocked = GuardedLong(1)
    val totalFish = GuardedLong(0)
    val totalGolden = GuardedLong(0)
    var levelWins = 0
    var noHitWins = 0
    var bossWins = 0
    var speedWins = 0
    var maxCombo = 0
    var maxCoinsHeld = 0L
    var purchases = 0
    val owned = LinkedHashSet<String>(Catalog.defaults)
    var boat = "classic"
    var sail = "white"
    var sailor = "ginger"
    val powerups = IntArray(PowerUp.entries.size) { 2 }
    val claimed = LinkedHashSet<String>()
    val notified = LinkedHashSet<String>()
    var dailyLastDay = -1L
    var dailyStreak = 0
    var dailyBestStreak = 0

    /** Passa: dzień (epoch day) ostatniego ukończonego poziomu i liczba dni z rzędu. */
    var streakLastDay = -1L
    var streak = 0
    var streakBest = 0

    /** Dzień rozpoczęcia bieżącej passy – od niego liczą się miesiące napraw. */
    var streakStartDay = -1L

    /** Naprawy passy: numer miesiąca passy i ile napraw w nim zużyto. */
    var repairPeriod = 0
    var repairsUsed = 0
    var repairsTotal = 0

    /** Trofea za passę – liczą się do punktów rankingowych. */
    val trophies = GuardedLong(0)
    var notificationsAsked = false

    /** Przypomnienia o passie i godzina pierwszego z nich. */
    var streakReminders = true
    var reminderHour = 18
    var keepScreenOn = true
    var lastSeenWall = 0L
    var music = true
    var sfx = true
    var vibration = true

    /** Wybrane sterowanie – gracz wybiera je przy pierwszym uruchomieniu, potem w ustawieniach. */
    var controls = ControlMode.SWIPE
    var controlsChosen = false
    var joystickLeft = true
    var tutorialDone = false
    var legacyMigrated = false
    var veteran = false
    var playSeconds = 0L
    var tamperEvents = 0
    val records = ArrayDeque<ScoreRecord>()

    /** Punkty rankingowe z poziomów dnia i tygodnia (suma wszystkich zaliczonych okresów). */
    val challengePoints = GuardedLong(0)

    /** Ostatnie zaliczone poziomy dnia/tygodnia – czy bieżący okres jest już ukończony, co wysłać na serwer. */
    val challengeResults = ArrayDeque<ChallengeResult>()

    /** Ostatnio pobrane seedy (poziom dnia/tygodnia działa potem także bez internetu, do końca okresu). */
    val challengeCache = ArrayList<Challenge>()

    /** Przypomnienia o nowym poziomie dnia i tygodnia oraz ich godzina (przed przypomnieniami o passie). */
    var dailyLevelReminders = true
    var weeklyLevelReminders = true
    var challengeReminderHour = ChallengeReminderPlan.DEFAULT_HOUR

    /** Poziomy graczy (id z serwera) ukończone na tym telefonie albo koncie. */
    val userLevelsDone = LinkedHashSet<Int>()

    /** Przejazdy poziomów graczy, których jeszcze nie udało się wysłać (np. bez internetu). */
    val userLevelQueue = ArrayDeque<UserLevelPlay>()

    /**
     * Rozpoczęty odbiór zarobku z targu: identyfikator prośby ponawianej, dopóki serwer nie odpowie
     * (ta sama prośba nie wypłaca drugi raz). Pusty – nic nie czeka.
     */
    var marketClaim = ""

    /** Liczba poziomów w tablicach rekordów i gwiazdek (co najmniej 1000). */
    val levelSlots: Int get() = stars.size

    /** Robi miejsce na rekord poziomu [level] (poziomy powyżej 1000 dochodzą po kolei, w paczkach po świecie). */
    fun ensureLevels(level: Int) {
        if (level <= stars.size) return
        val size = (level + LevelConstants.LEVELS_PER_WORLD - 1) / LevelConstants.LEVELS_PER_WORLD * LevelConstants.LEVELS_PER_WORLD
        resizeLevels(size.coerceAtMost(LevelConstants.MAX_LEVEL))
    }

    /** Przy resecie postępu wracamy do samej kampanii. */
    fun resizeLevels(size: Int) {
        best.resize(size)
        stars = stars.copyOf(size)
    }

    companion object {
        const val MAX_RECORDS = 50
        const val MAX_CHALLENGE_RESULTS = 20
        const val MAX_USER_LEVEL_QUEUE = 30
        const val MAX_USER_LEVELS_DONE = 5000
    }
}

/** Binarny zapis/odczyt [SaveData]. Szyfrowanie robi [SaveCrypto]. */
object SaveCodec {
    private const val MAGIC = 0x46505344 // "FPSD"
    private const val VERSION = 7

    fun encode(d: SaveData): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { o ->
            o.writeInt(MAGIC)
            o.writeShort(VERSION)
            o.writeLong(d.coins.get())
            // Liczba poziomów bez znaku (do 65535) – starsze wersje gry czytają z niej tylko pierwsze 1000.
            o.writeShort(d.levelSlots)
            for (i in 0 until d.levelSlots) {
                o.writeInt(d.best[i])
                o.writeByte(d.stars[i].toInt())
            }
            o.writeInt(d.unlocked.get().toInt())
            o.writeLong(d.totalFish.get())
            o.writeLong(d.totalGolden.get())
            o.writeInt(d.levelWins)
            o.writeInt(d.noHitWins)
            o.writeInt(d.bossWins)
            o.writeInt(d.speedWins)
            o.writeInt(d.maxCombo)
            o.writeLong(d.maxCoinsHeld)
            o.writeInt(d.purchases)
            writeSet(o, d.owned)
            o.writeUTF(d.boat)
            o.writeUTF(d.sail)
            o.writeUTF(d.sailor)
            o.writeShort(d.powerups.size)
            for (p in d.powerups) o.writeInt(p)
            writeSet(o, d.claimed)
            writeSet(o, d.notified)
            o.writeLong(d.dailyLastDay)
            o.writeInt(d.dailyStreak)
            o.writeInt(d.dailyBestStreak)
            o.writeLong(d.lastSeenWall)
            o.writeBoolean(d.music)
            o.writeBoolean(d.sfx)
            o.writeBoolean(d.vibration)
            o.writeBoolean(d.tutorialDone)
            o.writeBoolean(d.legacyMigrated)
            o.writeBoolean(d.veteran)
            o.writeLong(d.playSeconds)
            o.writeInt(d.tamperEvents)
            o.writeShort(d.records.size)
            for (r in d.records) {
                o.writeShort(r.level)
                o.writeInt(r.score)
                o.writeLong(r.timeMillis)
                o.writeShort(r.common)
                o.writeShort(r.blue)
                o.writeShort(r.golden)
                o.writeShort(r.hits)
                o.writeByte(r.maxCombo)
                o.writeByte(r.stages)
                o.writeLong(r.at)
            }
            // v2
            o.writeLong(d.streakLastDay)
            o.writeInt(d.streak)
            o.writeInt(d.streakBest)
            o.writeLong(d.trophies.get())
            o.writeBoolean(d.notificationsAsked)
            // v3
            o.writeLong(d.streakStartDay)
            o.writeInt(d.repairPeriod)
            o.writeInt(d.repairsUsed)
            o.writeInt(d.repairsTotal)
            o.writeBoolean(d.streakReminders)
            o.writeByte(d.reminderHour)
            o.writeBoolean(d.keepScreenOn)
            // v4
            o.writeByte(d.controls.ordinal)
            o.writeBoolean(d.controlsChosen)
            o.writeBoolean(d.joystickLeft)
            // v5
            o.writeLong(d.challengePoints.get())
            o.writeShort(d.challengeResults.size)
            for (r in d.challengeResults) {
                o.writeByte(r.kind.ordinal)
                o.writeInt(r.period)
                o.writeInt(r.score)
                o.writeInt(r.points)
                o.writeLong(r.timeMillis)
                o.writeShort(r.common)
                o.writeShort(r.blue)
                o.writeShort(r.golden)
                o.writeShort(r.hits)
                o.writeByte(r.maxCombo)
                o.writeByte(r.stages)
                o.writeLong(r.at)
            }
            o.writeShort(d.challengeCache.size)
            for (c in d.challengeCache) {
                o.writeByte(c.kind.ordinal)
                o.writeInt(c.period)
                o.writeLong(c.seed.value)
                o.writeLong(c.startsAt)
                o.writeLong(c.endsAt)
            }
            o.writeBoolean(d.dailyLevelReminders)
            o.writeBoolean(d.weeklyLevelReminders)
            o.writeByte(d.challengeReminderHour)
            // v7
            o.writeShort(d.userLevelsDone.size)
            for (id in d.userLevelsDone) o.writeInt(id)
            o.writeShort(d.userLevelQueue.size)
            for (p in d.userLevelQueue) {
                o.writeInt(p.levelId)
                o.writeBoolean(p.completed)
                o.writeInt(p.score)
                o.writeLong(p.timeMillis)
                o.writeShort(p.common)
                o.writeShort(p.blue)
                o.writeShort(p.golden)
                o.writeShort(p.hits)
                o.writeByte(p.maxCombo)
                o.writeByte(p.stages)
                o.writeLong(p.at)
            }
            o.writeUTF(d.marketClaim)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): SaveData {
        val d = SaveData()
        DataInputStream(ByteArrayInputStream(bytes)).use { i ->
            require(i.readInt() == MAGIC) { "Zły format zapisu" }
            val version = i.readShort().toInt()
            require(version in 1..VERSION) { "Nieznana wersja zapisu $version" }
            d.coins.set(i.readLong())
            val count = i.readUnsignedShort()
            d.ensureLevels(count.coerceAtMost(LevelConstants.MAX_LEVEL))
            for (n in 0 until count) {
                val best = i.readInt()
                val stars = i.readByte()
                if (n < d.levelSlots) {
                    d.best[n] = best
                    d.stars[n] = stars
                }
            }
            d.unlocked.set(i.readInt().toLong())
            d.totalFish.set(i.readLong())
            d.totalGolden.set(i.readLong())
            d.levelWins = i.readInt()
            d.noHitWins = i.readInt()
            d.bossWins = i.readInt()
            d.speedWins = i.readInt()
            d.maxCombo = i.readInt()
            d.maxCoinsHeld = i.readLong()
            d.purchases = i.readInt()
            readSet(i, d.owned)
            // Wygląd z nowszej wersji gry, którego ta nie zna, wraca do domyślnego (brak grafiki = błąd rysowania).
            d.boat = Catalog.knownOr(ItemKind.BOAT, i.readUTF(), "classic")
            d.sail = Catalog.knownOr(ItemKind.SAIL, i.readUTF(), "white")
            d.sailor = Catalog.knownOr(ItemKind.SAILOR, i.readUTF(), "ginger")
            val powerups = i.readShort().toInt()
            for (n in 0 until powerups) {
                val v = i.readInt()
                if (n < d.powerups.size) d.powerups[n] = v
            }
            readSet(i, d.claimed)
            readSet(i, d.notified)
            d.dailyLastDay = i.readLong()
            d.dailyStreak = i.readInt()
            d.dailyBestStreak = i.readInt()
            d.lastSeenWall = i.readLong()
            d.music = i.readBoolean()
            d.sfx = i.readBoolean()
            d.vibration = i.readBoolean()
            d.tutorialDone = i.readBoolean()
            d.legacyMigrated = i.readBoolean()
            d.veteran = i.readBoolean()
            d.playSeconds = i.readLong()
            d.tamperEvents = i.readInt()
            val records = i.readShort().toInt()
            repeat(records) {
                d.records += ScoreRecord(
                    level = i.readUnsignedShort(),
                    score = i.readInt(),
                    timeMillis = i.readLong(),
                    common = i.readShort().toInt(),
                    blue = i.readShort().toInt(),
                    golden = i.readShort().toInt(),
                    hits = i.readShort().toInt(),
                    maxCombo = i.readByte().toInt(),
                    stages = i.readByte().toInt(),
                    at = i.readLong(),
                )
            }
            if (version >= 2) {
                d.streakLastDay = i.readLong()
                d.streak = i.readInt()
                d.streakBest = i.readInt()
                d.trophies.set(i.readLong())
                d.notificationsAsked = i.readBoolean()
            }
            if (version >= 3) {
                d.streakStartDay = i.readLong()
                d.repairPeriod = i.readInt()
                d.repairsUsed = i.readInt()
                d.repairsTotal = i.readInt()
                d.streakReminders = i.readBoolean()
                d.reminderHour = i.readByte().toInt()
                d.keepScreenOn = i.readBoolean()
            } else if (d.streak > 0) {
                // Starsze zapisy nie znały początku passy – liczymy go wstecz od ostatniego dnia.
                d.streakStartDay = d.streakLastDay - d.streak + 1
            }
            if (version >= 4) {
                d.controls = ControlMode.entries.getOrElse(i.readByte().toInt()) { ControlMode.SWIPE }
                d.controlsChosen = i.readBoolean()
                d.joystickLeft = i.readBoolean()
            }
            if (version >= 5) {
                d.challengePoints.set(i.readLong())
                repeat(i.readUnsignedShort()) {
                    val kind = ChallengeKind.entries.getOrNull(i.readByte().toInt())
                    val result = ChallengeResult(
                        kind = kind ?: ChallengeKind.DAILY,
                        period = i.readInt(),
                        score = i.readInt(),
                        points = i.readInt(),
                        timeMillis = i.readLong(),
                        common = i.readShort().toInt(),
                        blue = i.readShort().toInt(),
                        golden = i.readShort().toInt(),
                        hits = i.readShort().toInt(),
                        maxCombo = i.readByte().toInt(),
                        stages = i.readByte().toInt(),
                        at = i.readLong(),
                    )
                    if (kind != null) d.challengeResults += result
                }
                repeat(i.readUnsignedShort()) {
                    val kind = ChallengeKind.entries.getOrNull(i.readByte().toInt())
                    val period = i.readInt()
                    val seed = i.readLong()
                    val startsAt = i.readLong()
                    val endsAt = i.readLong()
                    if (kind != null) d.challengeCache += Challenge(kind, period, LevelSeed(seed), startsAt, endsAt)
                }
            }
            if (version >= 6) {
                d.dailyLevelReminders = i.readBoolean()
                d.weeklyLevelReminders = i.readBoolean()
                d.challengeReminderHour = i.readByte().toInt()
            }
            if (version >= 7) {
                repeat(i.readUnsignedShort()) { d.userLevelsDone += i.readInt() }
                repeat(i.readUnsignedShort()) {
                    d.userLevelQueue += UserLevelPlay(
                        levelId = i.readInt(),
                        completed = i.readBoolean(),
                        score = i.readInt(),
                        timeMillis = i.readLong(),
                        common = i.readShort().toInt(),
                        blue = i.readShort().toInt(),
                        golden = i.readShort().toInt(),
                        hits = i.readShort().toInt(),
                        maxCombo = i.readByte().toInt(),
                        stages = i.readByte().toInt(),
                        at = i.readLong(),
                    )
                }
                d.marketClaim = i.readUTF()
            }
        }
        // Gra miała kiedyś mniej poziomów – kto ukończył ostatni z nich, dostaje od razu kolejny.
        val unlocked = d.unlocked.get().toInt().coerceIn(1, LevelConstants.MAX_LEVEL)
        if (unlocked < LevelConstants.LEVEL_COUNT && d.stars[unlocked - 1] > 0) d.unlocked.set(unlocked + 1L)
        d.ensureLevels(unlocked)
        return d
    }

    private fun writeSet(o: DataOutputStream, set: Set<String>) {
        o.writeShort(set.size)
        for (s in set) o.writeUTF(s)
    }

    private fun readSet(i: DataInputStream, into: MutableSet<String>) {
        into.clear()
        repeat(i.readShort().toInt()) { into += i.readUTF() }
    }
}
