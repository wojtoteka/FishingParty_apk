package wojtoteka.ovh.fishingparty.data

import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelPlan
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import wojtoteka.ovh.fishingparty.level.Scoring
import kotlin.math.max

class Reward(
    val accepted: Boolean,
    val coins: Int,
    val previousBest: Int,
    val newBest: Boolean,
    val newStars: Int,
    /** Passa po tym przejeździe, jeśli właśnie została przedłużona (0 = bez zmian). */
    val streakDays: Int = 0,
    val trophies: Int = 0,
    /** Jednorazowa nagroda za pierwsze ukończenie Wielkiego Finału (już wliczona w [coins]). */
    val finaleBonus: Int = 0,
    /** Telefon nie zna czasu serwera – dzień passy zaliczy się po połączeniu z internetem. */
    val streakDeferred: Boolean = false,
)

/**
 * @param days aktualna passa (0 = brak albo przerwana)
 * @param playedToday czy dziś ukończono już poziom (passa bezpieczna)
 */
class StreakState(val days: Int, val playedToday: Boolean)

/**
 * Oferta odzyskania przerwanej passy (jak naprawa passy w Duolingo).
 *
 * @param days passa, którą można odzyskać
 * @param missedDays ile dni zostało opuszczonych (każdy zużywa jedną naprawę)
 * @param cost łączna cena w monetach
 * @param used naprawy zużyte już w bieżącym miesiącu passy
 * @param resetDay dzień (epoch day), od którego licznik napraw liczy się od nowa
 */
class StreakRepair(val days: Int, val missedDays: Int, val cost: Int, val used: Int, val resetDay: Long)

/** @param needsOnline nagrody nie da się odebrać, dopóki telefon nie pobierze czasu z serwera */
class DailyState(val available: Boolean, val day: Int, val needsOnline: Boolean)

/**
 * Cała logika postępu gracza (bez Androida, więc testowalna na JVM).
 * Każda zmiana przechodzi przez tę klasę – UI nigdy nie modyfikuje zapisu bezpośrednio.
 *
 * Dni nagrody dziennej i passy liczy [GameTime] z czasu serwera – przestawienie
 * zegara w telefonie nie daje nowego dnia.
 */
class Progress(val save: SaveData, private val time: GameTime) {

    /** Do testów: stały czas i dzień ([today] = null – czas serwera nieznany). */
    constructor(save: SaveData, now: () -> Long, today: () -> Long?) : this(
        save,
        object : GameTime {
            override fun now(): Long = now()
            override fun today(): Long? = today()
            override fun localDay(): Long = today() ?: 0L
        },
    )

    private fun now(): Long = time.now()

    fun stars(level: Int): Int = if (level in 1..save.levelSlots) save.stars[level - 1].toInt() else 0

    fun best(level: Int): Int = if (level in 1..save.levelSlots) save.best[level - 1] else 0

    fun totalStars(): Int = save.stars.sum()

    fun perfectLevels(): Int = save.stars.count { it.toInt() == 3 }

    fun completedLevels(): Int = save.stars.count { it > 0 }

    /** Punkty rankingowe: suma najlepszych wyników ze wszystkich poziomów plus trofea za passę. */
    fun rankingPoints(): Long = save.best.sum() + save.trophies.get()

    val trophies: Long get() = save.trophies.get()

    fun highestUnlocked(): Int = save.unlocked.get().toInt().coerceIn(1, LevelConstants.MAX_LEVEL)

    /** Ile poziomów jest już w grze dla tego gracza: 1000 albo więcej, gdy płynie przez Bezkresne Morze. */
    fun levelsAvailable(): Int = maxOf(LevelConstants.LEVEL_COUNT, highestUnlocked())

    fun isUnlocked(level: Int): Boolean = level <= highestUnlocked()

    val coins: Long get() = save.coins.get()

    fun applyRun(result: RunResult, plan: LevelPlan): Reward {
        val level = result.level
        if (!ScoreValidator.isPlausible(plan, result) || !isUnlocked(level)) {
            save.tamperEvents++
            return Reward(false, 0, best(level), false, 0)
        }
        save.totalFish.add(result.fishCaught.toLong())
        save.totalGolden.add(result.goldenCaught.toLong())
        save.maxCombo = max(save.maxCombo, result.maxCombo)

        val previousBest = best(level)
        var newStars = 0
        var newBest = false
        var streakDays = 0
        var trophies = 0
        var finaleBonus = 0
        var streakDeferred = false
        if (result.completed) {
            val day = time.today()
            if (day == null) {
                // Komunikat tylko przy pierwszym takim poziomie – kolejne i tak liczą się do tego samego dnia.
                streakDeferred = !time.hasDeferredPlay()
                time.deferPlay()
            } else {
                trophies = creditStreakDay(day)
                if (trophies > 0) streakDays = save.streak
            }
            save.levelWins++
            if (result.hits == 0) save.noHitWins++
            if (plan.spec.type == LevelType.BOSS || plan.spec.type == LevelType.FINALE) save.bossWins++
            if (result.timeMillis <= plan.spec.parSeconds * 500L) save.speedWins++
            save.ensureLevels(level + 1)
            val previousStars = stars(level)
            if (plan.spec.type == LevelType.FINALE && previousStars == 0) finaleBonus = FINALE_BONUS
            if (result.stars > previousStars) {
                newStars = result.stars - previousStars
                save.stars[level - 1] = result.stars.toByte()
            }
            if (result.score > previousBest) {
                save.best[level - 1] = result.score
                newBest = true
            }
            if (level == highestUnlocked() && level < LevelConstants.MAX_LEVEL) save.unlocked.set(level + 1L)
            save.records.addLast(
                ScoreRecord(
                    level, result.score, result.timeMillis, result.commonCaught, result.blueCaught,
                    result.goldenCaught, result.hits, result.maxCombo, result.stagesCleared, now(),
                ),
            )
            while (save.records.size > SaveData.MAX_RECORDS) save.records.removeFirst()
        }
        val coins = Scoring.coinsFor(
            result.commonCaught, result.blueCaught, result.goldenCaught, result.completed, plan.spec.world, newStars,
        ) + finaleBonus
        addCoins(coins.toLong())
        return Reward(true, coins, previousBest, newBest, newStars, streakDays, trophies, finaleBonus, streakDeferred)
    }

    // ------------------------------------------------------------ passa

    fun streakState(): StreakState {
        // Bez czasu serwera tylko podgląd według daty telefonu – nagrody i tak czekają na serwer.
        val day = time.today() ?: time.localDay()
        return when {
            // Dzień zapisany „z przyszłości” (np. po przestawieniu zegara w starszej wersji) – passa stoi do tego dnia.
            save.streakLastDay >= day -> StreakState(save.streak, true)
            save.streakLastDay == day - 1 -> StreakState(save.streak, time.hasDeferredPlay())
            // Poziom ukończony bez czasu serwera po przerwie – zacznie nową passę.
            time.hasDeferredPlay() && time.today() == null -> StreakState(1, true)
            else -> StreakState(0, false)
        }
    }

    /**
     * Zalicza dzień [day] do passy (pierwszy ukończony poziom danego dnia przedłuża ją albo
     * zaczyna nową). Zwraca przyznane trofea, 0 = ten dzień był już zaliczony.
     * Wołane też później dla poziomów ukończonych, zanim telefon poznał czas serwera.
     */
    fun creditStreakDay(day: Long): Int {
        if (!extendStreak(day)) return 0
        val trophies = StreakRewards.trophiesFor(save.streak)
        save.trophies.add(trophies.toLong())
        return trophies
    }

    private fun extendStreak(day: Long): Boolean {
        if (day <= save.streakLastDay) return false
        if (save.streakLastDay == day - 1 && save.streak > 0) {
            save.streak++
        } else {
            save.streak = 1
            save.streakStartDay = day
            save.repairPeriod = 0
            save.repairsUsed = 0
        }
        save.streakLastDay = day
        save.streakBest = max(save.streakBest, save.streak)
        return true
    }

    /** Naprawy zużyte w miesiącu passy, do którego należy [day] (liczone od dnia jej rozpoczęcia). */
    private fun repairsUsedIn(day: Long): Int =
        if (StreakRepairs.period(save.streakStartDay, day) == save.repairPeriod) save.repairsUsed else 0

    /** Przerwana passa, którą da się jeszcze odzyskać – albo null, gdy nie ma czego (lub już przepadła). */
    fun streakRepair(): StreakRepair? {
        if (save.streak <= 0 || save.streakLastDay < 0 || time.hasDeferredPlay()) return null
        val day = time.today() ?: return null
        val missed = (day - 1 - save.streakLastDay).toInt()
        if (missed < 1 || missed > StreakRepairs.MAX_MISSED_DAYS) return null
        val used = repairsUsedIn(day)
        if (used + missed > StreakRepairs.PER_MONTH) return null
        val cost = (used until used + missed).sumOf { StreakRepairs.price(it) }
        val reset = StreakRepairs.periodStart(save.streakStartDay, StreakRepairs.period(save.streakStartDay, day) + 1)
        return StreakRepair(save.streak, missed, cost, used, reset)
    }

    /** Odzyskuje passę za monety: opuszczone dni są „załatane”, dziś wystarczy ukończyć poziom. */
    fun repairStreak(): Boolean {
        val offer = streakRepair() ?: return false
        val day = time.today() ?: return false
        if (!spend(offer.cost)) return false
        save.repairPeriod = StreakRepairs.period(save.streakStartDay, day)
        save.repairsUsed = offer.used + offer.missedDays
        save.repairsTotal += offer.missedDays
        save.streakLastDay = day - 1
        touchClock()
        return true
    }

    /** Przerwana passa, której nie da się już odzyskać (do komunikatu „passa przepadła”). */
    fun streakLost(): Boolean {
        val day = time.today() ?: return false
        return save.streak > 0 && save.streakLastDay < day - 1 && !time.hasDeferredPlay() && streakRepair() == null
    }

    fun addCoins(amount: Long) {
        val total = save.coins.add(amount)
        save.maxCoinsHeld = max(save.maxCoinsHeld, total)
    }

    fun spend(amount: Int): Boolean {
        if (amount < 0 || save.coins.get() < amount) return false
        save.coins.add(-amount.toLong())
        return true
    }

    // ------------------------------------------------------------ sklep

    fun owns(item: ShopItem): Boolean = item.key in save.owned

    fun isEquipped(item: ShopItem): Boolean = when (item.kind) {
        ItemKind.BOAT -> save.boat == item.id
        ItemKind.SAIL -> save.sail == item.id
        ItemKind.SAILOR -> save.sailor == item.id
        ItemKind.POWERUP -> false
    }

    fun buy(item: ShopItem): Boolean {
        if (item.kind != ItemKind.POWERUP && owns(item)) return false
        if (!spend(item.price)) return false
        save.purchases++
        if (item.kind == ItemKind.POWERUP) {
            val p = PowerUp.entries.first { it.id == item.id }
            save.powerups[p.ordinal]++
        } else {
            save.owned += item.key
            equip(item)
        }
        return true
    }

    fun equip(item: ShopItem) {
        if (!owns(item)) return
        when (item.kind) {
            ItemKind.BOAT -> save.boat = item.id
            ItemKind.SAIL -> save.sail = item.id
            ItemKind.SAILOR -> save.sailor = item.id
            ItemKind.POWERUP -> Unit
        }
    }

    fun powerups(p: PowerUp): Int = save.powerups[p.ordinal]

    /** Zużywa wybrane wzmocnienia; zwraca tylko te, które gracz faktycznie miał. */
    fun consume(selected: Set<PowerUp>): Loadout {
        val used = selected.filter { save.powerups[it.ordinal] > 0 }.toSet()
        for (p in used) save.powerups[p.ordinal]--
        return Loadout(
            magnet = PowerUp.MAGNET in used,
            shield = PowerUp.SHIELD in used,
            turbo = PowerUp.TURBO in used,
            extraLife = PowerUp.LIFE in used,
            net = PowerUp.NET in used,
            frost = PowerUp.FROST in used,
            lure = PowerUp.LURE in used,
            rhythm = PowerUp.RHYTHM in used,
        )
    }

    // ------------------------------------------------------------ nagroda dzienna

    /** Zapamiętuje najpóźniejszy widziany czas gry. */
    fun touchClock() {
        val t = now()
        if (t > save.lastSeenWall) save.lastSeenWall = t
    }

    fun dailyState(): DailyState {
        val day = time.today() ?: return DailyState(false, save.dailyStreak, true)
        // Odebrana dziś – albo zapisana na dzień „z przyszłości” (zegar przestawiony w starszej wersji gry).
        if (save.dailyLastDay >= day) return DailyState(false, save.dailyStreak, false)
        val next = if (save.dailyLastDay == day - 1) save.dailyStreak % 7 + 1 else 1
        return DailyState(true, next, false)
    }

    fun claimDaily(): Int {
        val today = time.today() ?: return 0
        val state = dailyState()
        if (!state.available) return 0
        val coins = DailyRewards.coins[state.day - 1]
        addCoins(coins.toLong())
        if (state.day == DailyRewards.BONUS_DAY) for (i in save.powerups.indices) save.powerups[i]++
        save.dailyStreak = state.day
        save.dailyBestStreak = max(save.dailyBestStreak, state.day)
        save.dailyLastDay = today
        touchClock()
        return coins
    }

    // ------------------------------------------------------------ osiągnięcia

    fun metric(m: Metric): Long = when (m) {
        Metric.FISH -> save.totalFish.get()
        Metric.GOLDEN -> save.totalGolden.get()
        Metric.LEVELS -> completedLevels().toLong()
        Metric.STARS -> totalStars().toLong()
        Metric.PERFECT -> perfectLevels().toLong()
        Metric.NO_HIT -> save.noHitWins.toLong()
        Metric.COMBO -> save.maxCombo.toLong()
        Metric.BOSS -> save.bossWins.toLong()
        Metric.PURCHASES -> save.purchases.toLong()
        Metric.COSMETICS -> save.owned.count { it !in Catalog.defaults }.toLong()
        Metric.BOATS -> save.owned.count { it.startsWith("boat:") }.toLong()
        Metric.DAILY_STREAK -> save.dailyBestStreak.toLong()
        Metric.STREAK -> save.streakBest.toLong()
        Metric.SPEED -> save.speedWins.toLong()
        Metric.COINS_HELD -> save.maxCoinsHeld
        Metric.VETERAN -> if (save.veteran) 1 else 0
    }

    fun isUnlocked(a: Achievement): Boolean = metric(a.metric) >= a.target

    fun isClaimed(a: Achievement): Boolean = a.id in save.claimed

    fun claimable(): List<Achievement> = Achievements.all.filter { isUnlocked(it) && !isClaimed(it) }

    fun claim(a: Achievement): Int {
        if (!isUnlocked(a) || isClaimed(a)) return 0
        save.claimed += a.id
        addCoins(a.reward.toLong())
        return a.reward
    }

    /** Osiągnięcia zdobyte od ostatniego sprawdzenia (do baneru „Osiągnięcie!”). */
    fun takeNewAchievements(): List<Achievement> {
        val fresh = Achievements.all.filter { isUnlocked(it) && it.id !in save.notified }
        save.notified += fresh.map { it.id }
        return fresh
    }

    /** Czyści postęp (ustawienia dźwięku zostają). */
    fun resetProgress() {
        save.coins.set(0)
        save.resizeLevels(LevelConstants.LEVEL_COUNT)
        for (i in 0 until LevelConstants.LEVEL_COUNT) {
            save.best[i] = 0
            save.stars[i] = 0
        }
        save.unlocked.set(1)
        save.totalFish.set(0)
        save.totalGolden.set(0)
        save.levelWins = 0
        save.noHitWins = 0
        save.bossWins = 0
        save.speedWins = 0
        save.maxCombo = 0
        save.maxCoinsHeld = 0
        save.purchases = 0
        save.owned.clear()
        save.owned += Catalog.defaults
        save.boat = "classic"
        save.sail = "white"
        save.sailor = "ginger"
        for (i in save.powerups.indices) save.powerups[i] = 2
        save.claimed.clear()
        save.notified.clear()
        save.dailyLastDay = -1
        save.dailyStreak = 0
        save.dailyBestStreak = 0
        save.streakLastDay = -1
        save.streak = 0
        save.streakBest = 0
        save.streakStartDay = -1
        save.repairPeriod = 0
        save.repairsUsed = 0
        save.repairsTotal = 0
        save.trophies.set(0)
        save.veteran = false
        save.records.clear()
    }

    companion object {
        /** Monety za pierwsze ukończenie poziomu 1000. */
        const val FINALE_BONUS = 5000
    }
}
