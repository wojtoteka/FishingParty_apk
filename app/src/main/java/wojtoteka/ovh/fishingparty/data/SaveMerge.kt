package wojtoteka.ovh.fishingparty.data

import kotlin.math.max

/**
 * Łączenie postępu z dwóch telefonów jednego konta (np. grającego równocześnie albo bez sieci).
 *
 * [base] to zapis, który ten telefon ostatnio wymienił z kontem, [local] – stan tego telefonu,
 * [remote] – nowszy zapis z konta (od drugiego telefonu). Z obu stron zostaje wszystko, co zdobyto
 * od [base]: monety i liczniki to [remote] + to, co doszło na tym telefonie (więc nic nie przepada
 * ani nie liczy się podwójnie), rekordy, gwiazdki i odblokowane poziomy – lepsze z obu,
 * kupione przedmioty, odebrane osiągnięcia i zaliczone poziomy dnia/tygodnia – suma.
 * Ustawienia i założony wygląd łodzi: zmienione na tym telefonie wygrywają, inaczej zostają z konta.
 *
 * Wynik to nowy obiekt; [base], [local] i [remote] zostają bez zmian.
 */
object SaveMerge {

    fun merge(base: SaveData, local: SaveData, remote: SaveData): SaveData {
        val out = SaveCodec.decode(SaveCodec.encode(remote))

        // ---------------------------------------------------------------- liczniki: konto + przyrost tego telefonu
        fun sum(b: Long, l: Long, r: Long) = (r + (l - b)).coerceAtLeast(0L)
        fun sum(b: Int, l: Int, r: Int) = (r + (l - b)).coerceAtLeast(0)
        out.coins.set(sum(base.coins.get(), local.coins.get(), remote.coins.get()))
        out.totalFish.set(sum(base.totalFish.get(), local.totalFish.get(), remote.totalFish.get()))
        out.totalGolden.set(sum(base.totalGolden.get(), local.totalGolden.get(), remote.totalGolden.get()))
        out.levelWins = sum(base.levelWins, local.levelWins, remote.levelWins)
        out.noHitWins = sum(base.noHitWins, local.noHitWins, remote.noHitWins)
        out.bossWins = sum(base.bossWins, local.bossWins, remote.bossWins)
        out.speedWins = sum(base.speedWins, local.speedWins, remote.speedWins)
        out.purchases = sum(base.purchases, local.purchases, remote.purchases)
        out.playSeconds = sum(base.playSeconds, local.playSeconds, remote.playSeconds)
        out.repairsTotal = sum(base.repairsTotal, local.repairsTotal, remote.repairsTotal)
        out.tamperEvents = sum(base.tamperEvents, local.tamperEvents, remote.tamperEvents)
        for (i in out.powerups.indices) out.powerups[i] = sum(base.powerups[i], local.powerups[i], remote.powerups[i])
        // Nagrody, które da się odebrać tylko raz, a odebrał je każdy telefon osobno – liczą się raz.
        out.coins.set((out.coins.get() - twiceClaimed(base, local, remote)).coerceAtLeast(0L))
        if (local.dailyLastDay == remote.dailyLastDay && local.dailyLastDay > base.dailyLastDay && local.dailyStreak == DailyRewards.BONUS_DAY) {
            for (i in out.powerups.indices) out.powerups[i] = (out.powerups[i] - 1).coerceAtLeast(0)
        }

        // ---------------------------------------------------------------- rekordy: lepsze z obu
        val slots = max(local.levelSlots, remote.levelSlots)
        out.ensureLevels(slots)
        for (i in 0 until slots) {
            if (i < local.levelSlots) {
                if (local.best[i] > out.best[i]) out.best[i] = local.best[i]
                if (local.stars[i] > out.stars[i]) out.stars[i] = local.stars[i]
            }
        }
        out.unlocked.set(max(local.unlocked.get(), remote.unlocked.get()))
        out.maxCombo = max(local.maxCombo, remote.maxCombo)
        out.maxCoinsHeld = max(local.maxCoinsHeld, remote.maxCoinsHeld)
        out.lastSeenWall = max(local.lastSeenWall, remote.lastSeenWall)

        // ---------------------------------------------------------------- przedmioty i osiągnięcia: suma
        out.owned += local.owned
        out.claimed += local.claimed
        out.notified += local.notified
        out.tutorialDone = local.tutorialDone || remote.tutorialDone
        out.legacyMigrated = local.legacyMigrated || remote.legacyMigrated
        out.veteran = local.veteran || remote.veteran
        out.notificationsAsked = local.notificationsAsked || remote.notificationsAsked

        // ---------------------------------------------------------------- codzienna nagroda i passa: dalsza z obu
        if (local.dailyLastDay > remote.dailyLastDay) {
            out.dailyLastDay = local.dailyLastDay
            out.dailyStreak = local.dailyStreak
        }
        out.dailyBestStreak = max(local.dailyBestStreak, remote.dailyBestStreak)
        mergeStreak(base, local, remote, out)

        // ---------------------------------------------------------------- przejazdy i poziomy dnia/tygodnia
        val records = (remote.records + local.records).distinctBy { it.at to it.level }.sortedBy { it.at }
        out.records.clear()
        out.records += records.takeLast(SaveData.MAX_RECORDS)
        mergeChallenges(base, local, remote, out)

        // ---------------------------------------------------------------- ustawienia: zmienione tutaj wygrywają
        fun <T> pick(b: T, l: T, r: T) = if (l != b) l else r
        out.boat = pick(base.boat, local.boat, remote.boat)
        out.sail = pick(base.sail, local.sail, remote.sail)
        out.sailor = pick(base.sailor, local.sailor, remote.sailor)
        out.streakReminders = pick(base.streakReminders, local.streakReminders, remote.streakReminders)
        out.reminderHour = pick(base.reminderHour, local.reminderHour, remote.reminderHour)
        out.keepScreenOn = pick(base.keepScreenOn, local.keepScreenOn, remote.keepScreenOn)
        out.music = pick(base.music, local.music, remote.music)
        out.sfx = pick(base.sfx, local.sfx, remote.sfx)
        out.vibration = pick(base.vibration, local.vibration, remote.vibration)
        out.dailyLevelReminders = pick(base.dailyLevelReminders, local.dailyLevelReminders, remote.dailyLevelReminders)
        out.weeklyLevelReminders = pick(base.weeklyLevelReminders, local.weeklyLevelReminders, remote.weeklyLevelReminders)
        out.challengeReminderHour = pick(base.challengeReminderHour, local.challengeReminderHour, remote.challengeReminderHour)
        // Sterowanie należy do telefonu (inny ekran, inna ręka).
        out.controls = local.controls
        out.controlsChosen = local.controlsChosen
        out.joystickLeft = local.joystickLeft
        return out
    }

    /** Monety za nagrody odebrane od [base] na obu telefonach: dzienna nagroda, osiągnięcia, premia za poziom dnia/tygodnia. */
    private fun twiceClaimed(base: SaveData, local: SaveData, remote: SaveData): Long {
        var coins = 0L
        if (local.dailyLastDay == remote.dailyLastDay && local.dailyLastDay > base.dailyLastDay) {
            coins += DailyRewards.coins.getOrElse(local.dailyStreak - 1) { 0 }
        }
        for (a in Achievements.all) {
            if (a.id in local.claimed && a.id in remote.claimed && a.id !in base.claimed) coins += a.reward
        }
        fun key(r: ChallengeResult) = r.kind to r.period
        val before = base.challengeResults.map(::key).toSet()
        val remoteKeys = remote.challengeResults.map(::key).toSet()
        for (r in local.challengeResults) {
            if (key(r) in remoteKeys && key(r) !in before) coins += r.kind.coinBonus
        }
        return coins
    }

    /**
     * Passa należy do dni: zostaje ta, która sięga dalej. Trofea za dni zaliczone tylko na jednym
     * telefonie się sumują; jeśli oba telefony liczyły passę w tych samych dniach, zostaje większa liczba.
     */
    private fun mergeStreak(base: SaveData, local: SaveData, remote: SaveData, out: SaveData) {
        val localAdvanced = local.streakLastDay > base.streakLastDay
        val remoteAdvanced = remote.streakLastDay > base.streakLastDay
        val trophies = if (localAdvanced && remoteAdvanced) {
            max(local.trophies.get(), remote.trophies.get())
        } else {
            (remote.trophies.get() + (local.trophies.get() - base.trophies.get())).coerceAtLeast(0L)
        }
        out.trophies.set(trophies)
        if (local.streakLastDay > remote.streakLastDay ||
            (local.streakLastDay == remote.streakLastDay && local.streak > remote.streak)
        ) {
            out.streakLastDay = local.streakLastDay
            out.streak = local.streak
            out.streakStartDay = local.streakStartDay
            out.repairPeriod = local.repairPeriod
            out.repairsUsed = local.repairsUsed
        }
        out.streakBest = max(local.streakBest, remote.streakBest)
    }

    /**
     * Zaliczony okres liczy się raz (tak jak w rankingu): punkty to te z konta plus punkty
     * okresów zaliczonych tylko na tym telefonie.
     */
    private fun mergeChallenges(base: SaveData, local: SaveData, remote: SaveData, out: SaveData) {
        fun key(r: ChallengeResult) = r.kind to r.period
        val known = remote.challengeResults.map(::key).toSet()
        val before = base.challengeResults.map(::key).toSet()
        val onlyHere = local.challengeResults.filter { key(it) !in known && key(it) !in before }
        val duplicated = local.challengeResults.filter { key(it) in known && key(it) !in before }.sumOf { it.points.toLong() }
        val points = remote.challengePoints.get() + (local.challengePoints.get() - base.challengePoints.get()) - duplicated
        out.challengePoints.set(max(points, remote.challengePoints.get()))
        val results = (remote.challengeResults + onlyHere).sortedBy { it.at }
        out.challengeResults.clear()
        out.challengeResults += results.takeLast(SaveData.MAX_CHALLENGE_RESULTS)
        // Seedy: po jednym na rodzaj, świeższy z obu (każdy telefon pobiera je sam, to tylko zapas na brak sieci).
        val seeds = (local.challengeCache + remote.challengeCache).groupBy { it.kind }.values.map { same -> same.maxBy { it.period } }
        out.challengeCache.clear()
        out.challengeCache += seeds
    }
}
