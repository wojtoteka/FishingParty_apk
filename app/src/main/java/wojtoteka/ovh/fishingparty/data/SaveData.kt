package wojtoteka.ovh.fishingparty.data

import wojtoteka.ovh.fishingparty.core.GuardedIntArray
import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.level.LevelConstants
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

/** Cały stan gracza. Najważniejsze liczby trzymane są w pamięci w postaci zamaskowanej. */
class SaveData {
    val coins = GuardedLong(0)
    val best = GuardedIntArray(LevelConstants.LEVEL_COUNT)
    val stars = ByteArray(LevelConstants.LEVEL_COUNT)
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
    var tutorialDone = false
    var legacyMigrated = false
    var veteran = false
    var playSeconds = 0L
    var tamperEvents = 0
    val records = ArrayDeque<ScoreRecord>()

    companion object {
        const val MAX_RECORDS = 50
    }
}

/** Binarny zapis/odczyt [SaveData]. Szyfrowanie robi [SaveCrypto]. */
object SaveCodec {
    private const val MAGIC = 0x46505344 // "FPSD"
    private const val VERSION = 3

    fun encode(d: SaveData): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { o ->
            o.writeInt(MAGIC)
            o.writeShort(VERSION)
            o.writeLong(d.coins.get())
            o.writeShort(LevelConstants.LEVEL_COUNT)
            for (i in 0 until LevelConstants.LEVEL_COUNT) {
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
            val count = i.readShort().toInt()
            for (n in 0 until count) {
                val best = i.readInt()
                val stars = i.readByte()
                if (n < LevelConstants.LEVEL_COUNT) {
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
            d.boat = i.readUTF()
            d.sail = i.readUTF()
            d.sailor = i.readUTF()
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
                    level = i.readShort().toInt(),
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
        }
        // Gra miała kiedyś mniej poziomów – kto ukończył ostatni z nich, dostaje od razu kolejny.
        val unlocked = d.unlocked.get().toInt().coerceIn(1, LevelConstants.LEVEL_COUNT)
        if (unlocked < LevelConstants.LEVEL_COUNT && d.stars[unlocked - 1] > 0) d.unlocked.set(unlocked + 1L)
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
