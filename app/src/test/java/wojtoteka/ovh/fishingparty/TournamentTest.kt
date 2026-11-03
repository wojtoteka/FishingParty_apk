package wojtoteka.ovh.fishingparty

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.data.SaveMerge
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelPlan
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import wojtoteka.ovh.fishingparty.level.SeedProfile
import wojtoteka.ovh.fishingparty.level.Tournament
import wojtoteka.ovh.fishingparty.level.Tournaments
import wojtoteka.ovh.fishingparty.online.TournamentJson

class TournamentTest {

    private val start = 1_800_000_000_000L
    private val end = start + 2 * 86_400_000L
    private val storm = SeedProfile(minDifficulty = 1000, maxDifficulty = 1300, minStages = 5, maxStages = 6)

    private fun tournament(id: Int = 2966, seed: Long = Rng.mix(0x7041L, id.toLong())) =
        Tournament(id, "storm", "Sztorm", "Storm", start, end, LevelSeed(seed), storm, 1000)

    private fun win(plan: LevelPlan, score: Int, completed: Boolean = true) = RunResult(
        level = Tournaments.LEVEL_NUMBER,
        completed = completed,
        score = score,
        stars = 1,
        starFlags = booleanArrayOf(true, false, false),
        timeMillis = (ScoreValidator.minimumSeconds(plan) * 4000).toLong(),
        commonCaught = plan.stages.sumOf { it.common },
        blueCaught = 0,
        goldenCaught = 0,
        hits = 1,
        maxCombo = 2,
        stagesCleared = if (completed) plan.stages.size else 1,
    )

    @Test
    fun sameSeedAndRulesGiveSameLevelForEveryone() {
        val a = tournament().plan()
        val b = tournament().plan()
        assertEquals(a.spec.type, b.spec.type)
        assertEquals(a.stages.map { it.rows to it.quota }, b.stages.map { it.rows to it.quota })
        assertEquals(LevelGenerator.maxScore(a), LevelGenerator.maxScore(b))
        assertEquals(Tournaments.LEVEL_NUMBER, a.spec.number)
        // Reguły świata z serwera: liczba etapów w ich granicach (wyścig z czasem ma najwyżej 4).
        for (i in 0 until 100) {
            val plan = tournament(seed = Rng.mix(0x51L, i.toLong())).plan()
            assertTrue(plan.stages.size in 4..6)
            LevelGenerator.generate(plan)
        }
        // Inny seed – inna plansza.
        val different = (1..20).count { LevelGenerator.maxScore(tournament(seed = Rng.mix(9L, it.toLong())).plan()) != LevelGenerator.maxScore(a) }
        assertTrue(different > 15)
    }

    @Test
    fun parsesServerResponse() {
        val json = JSONObject(
            """
            {"now": ${start + 1000}, "zone": "Europe/Warsaw",
             "current": {"id": 2966, "theme": "storm", "name": {"pl": "Sztorm", "en": "Storm"}, "startsAt": $start, "endsAt": $end,
                         "seed": "-4611686018427387904", "profile": {"minDifficulty": 1000, "maxDifficulty": 1300, "minStages": 5, "maxStages": 6}, "position": 1000},
             "next": {"id": 2967, "theme": "sprint", "name": {"pl": "Sprint", "en": "Sprint"}, "startsAt": ${start + 7 * 86_400_000L}, "endsAt": ${end + 7 * 86_400_000L}},
             "previous": {"id": 2965, "theme": "reef", "name": {"pl": "Puchar Rafy", "en": "Reef Cup"}, "startsAt": ${start - 7 * 86_400_000L},
                          "endsAt": ${end - 7 * 86_400_000L}, "finalized": true, "participants": 12,
                          "top": [{"rank": 1, "id": 5, "nick": "Rybak", "score": 9000, "prize": {"points": 5000, "coins": 2000}}]},
             "prizes": [{"from": 1, "to": 1, "points": 5000, "coins": 2000}, {"from": 101, "to": null, "points": 200, "coins": 100}]}
            """.trimIndent(),
        )
        val info = TournamentJson.info(json)
        val current = info.current!!
        assertTrue(current.playable)
        assertEquals(-4611686018427387904L, current.seed!!.value)
        assertEquals(5, current.profile!!.minStages)
        assertEquals("Storm", current.name(polish = false))
        assertTrue(current.isRunning(start + 1000))
        assertFalse(info.next!!.playable)
        assertEquals("Sprint", info.next!!.name(polish = true))
        assertTrue(info.previousFinalized)
        assertEquals(12, info.previousPlayers)
        assertEquals("Rybak", info.previousTop.single().nick)
        assertEquals(2000, info.previousTop.single().prizeCoins)
        assertEquals(null, info.prizes.last().to)

        // Reguły, z których gra nie zbuduje planszy – turniej widać, ale nie da się w niego zagrać.
        val broken = JSONObject(json.getJSONObject("current").toString()).put("profile", JSONObject().put("minDifficulty", 5).put("maxDifficulty", 1).put("minStages", 2).put("maxStages", 9))
        assertFalse(TournamentJson.tournament(broken)!!.playable)

        val board = TournamentJson.board(
            JSONObject(
                """
                {"tournament": {"id": 2966, "theme": "storm", "name": {"pl": "Sztorm", "en": "Storm"}, "startsAt": $start, "endsAt": $end},
                 "finalized": false, "participants": 2,
                 "entries": [{"rank": 1, "id": 7, "nick": "A", "score": 900}, {"rank": 2, "id": 8, "nick": "B", "score": 800}],
                 "me": {"id": 8, "nick": "B", "score": 800, "rank": 2, "prize": null}}
                """.trimIndent(),
            ),
        )!!
        assertEquals(2, board.entries.size)
        assertEquals(8L, board.myId)
        assertEquals(2, board.me!!.rank)
        assertEquals(0, board.me!!.prizeCoins)
        val guest = TournamentJson.board(JSONObject().put("tournament", json.getJSONObject("next")).put("me", JSONObject.NULL))!!
        assertNull(guest.me)
        assertNull(guest.myId)
    }

    @Test
    fun bestRunPerTournamentIsKeptAndQueued() {
        val save = SaveData()
        var now = start + 60_000
        val progress = Progress(save, { now }, { 20_000L })
        val t = tournament()
        assertTrue(progress.cacheTournament(t))
        assertFalse(progress.cacheTournament(tournament()))
        assertNotNull(progress.currentTournament())
        val plan = t.plan()

        val first = progress.applyTournament(win(plan, 500), plan, t)
        assertTrue(first.accepted)
        assertTrue(first.newBest)
        assertEquals(500, first.tournamentBest)
        assertEquals(1, first.streakDays)
        assertTrue(first.coins > 0)
        assertEquals(500, progress.tournamentBest(t.id))
        assertEquals(now, save.tournamentRuns.single().at)

        // Słabsza próba: monety za ryby tak, ale nie zastępuje rekordu i nie trafia do wysyłki.
        now += 60_000
        val weaker = progress.applyTournament(win(plan, 300), plan, t)
        assertFalse(weaker.newBest)
        assertEquals(500, weaker.tournamentBest)
        assertEquals(1, save.tournamentRuns.size)
        // Przegrana nic nie zapisuje.
        assertFalse(progress.applyTournament(win(plan, 900, completed = false), plan, t).newBest)
        assertEquals(500, progress.tournamentBest(t.id))
        // Lepsza – zastępuje.
        now += 60_000
        assertTrue(progress.applyTournament(win(plan, 700), plan, t).newBest)
        assertEquals(700, save.tournamentRuns.single().score)
        assertEquals(now, save.tournamentRuns.single().at)
        // Wynik ponad limit planszy jest odrzucany.
        assertFalse(progress.applyTournament(win(plan, 10_000_000), plan, t).accepted)
        assertEquals(700, progress.tournamentBest(t.id))

        // Najbliższy turniej (bez seeda) nie wypiera trwającego, a po końcu turnieju trzeba zapytać serwer.
        val upcoming = Tournament(t.id + 1, "sprint", "Sprint", "Sprint", end + 5 * 86_400_000L, end + 7 * 86_400_000L)
        assertFalse(progress.cacheTournament(upcoming))
        now = end + 1
        assertNull(progress.currentTournament())
        assertTrue(progress.cacheTournament(upcoming))
        assertNull(progress.currentTournament())
    }

    @Test
    fun tournamentDataSurvivesSaveAndMerge() {
        val save = SaveData()
        val now = start + 60_000
        val progress = Progress(save, { now }, { 20_000L })
        val t = tournament()
        progress.cacheTournament(t)
        val plan = t.plan()
        progress.applyTournament(win(plan, 640), plan, t)
        save.prizeClaim = "0123456789abcdef0123456789abcdef"

        val copy = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(640, copy.tournamentRuns.single().score)
        assertEquals(t.id, copy.tournamentRuns.single().tournament)
        assertTrue(t.sameAs(copy.tournamentCache))
        assertEquals(LevelGenerator.maxScore(plan), LevelGenerator.maxScore(copy.tournamentCache!!.plan()))
        assertEquals(save.prizeClaim, copy.prizeClaim)
        // Turniej bez seeda (najbliższy) też się zapisuje.
        save.tournamentCache = Tournament(5, "sprint", "Sprint", "Sprint", end, end + 1000)
        assertFalse(SaveCodec.decode(SaveCodec.encode(save)).tournamentCache!!.playable)

        // Dwa telefony: z każdego turnieju zostaje lepszy przejazd.
        val base = SaveCodec.decode(SaveCodec.encode(SaveData()))
        val local = SaveCodec.decode(SaveCodec.encode(save))
        val remote = SaveData()
        val other = Tournament(t.id - 1, "reef", "Puchar Rafy", "Reef Cup", start - 7 * 86_400_000L, end - 7 * 86_400_000L, LevelSeed(77L), storm, 900)
        val remoteProgress = Progress(remote, { other.startsAt + 1000 }, { 19_990L })
        val otherPlan = other.plan()
        remoteProgress.applyTournament(win(otherPlan, 450), otherPlan, other)
        val remoteNow = Progress(remote, { now }, { 20_000L })
        remoteNow.applyTournament(win(plan, 900), plan, t)
        val merged = SaveMerge.merge(base, local, remote)
        assertEquals(listOf(other.id to 450, t.id to 900), merged.tournamentRuns.map { it.tournament to it.score })
        assertEquals(save.prizeClaim, merged.prizeClaim)
        assertEquals(5, merged.tournamentCache!!.id)
    }
}
