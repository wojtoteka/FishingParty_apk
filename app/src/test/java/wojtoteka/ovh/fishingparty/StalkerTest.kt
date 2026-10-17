package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.Hunt
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Phase
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.Challenges
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.EnemySpawn
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelLayout
import wojtoteka.ovh.fishingparty.level.LevelPlan
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.LevelSpec
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.SeedLevels
import wojtoteka.ovh.fishingparty.level.StageLayout
import wojtoteka.ovh.fishingparty.level.Stalkers
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import kotlin.math.atan2
import kotlin.math.ceil

/** Widmokuda: gdzie się pojawia i jak poluje (wzrok blokowany przez przeszkody, ostrzeżenie przed skokiem). */
class StalkerTest {

    private fun stalkers(plan: LevelPlan) = plan.stages.sumOf { s -> s.enemies.count { it == EnemyKind.STALKER } }

    @Test
    fun introducedOnLevel20AfterTheShark() {
        for (n in 1 until Stalkers.FIRST_LEVEL) assertEquals("poziom $n", 0, stalkers(LevelGenerator.plan(n)))
        // Poziomy 20–23 przedstawiają widmokudę: dokładnie jedna, w drugim etapie.
        for (n in Stalkers.FIRST_LEVEL until Stalkers.FIRST_LEVEL + Stalkers.INTRO_LEVELS) {
            val plan = LevelGenerator.plan(n)
            assertEquals("poziom $n", 1, stalkers(plan))
            assertTrue("poziom $n", EnemyKind.STALKER in plan.stages[1].enemies)
        }
        // Dalej pojawia się coraz częściej – na końcu drugiej wyprawy w większości poziomów.
        fun share(range: IntRange) = range.count { stalkers(LevelGenerator.plan(it)) > 0 } / range.count().toFloat()
        assertTrue(share(24..100) in 0.1f..0.5f)
        assertTrue(share(800..999) > 0.7f)
        assertTrue(stalkers(LevelGenerator.plan(1000)) >= 3)
    }

    @Test
    fun replacesWeakerMonstersInsteadOfAddingMore() {
        val levels = (1..1000) + (1001..1400) + (19_900..20_000)
        for (n in levels) {
            for ((k, stage) in LevelGenerator.plan(n).stages.withIndex()) {
                // Zamiast tłumu rekinów – najwyżej dwa i widmokuda.
                assertTrue("poziom $n etap $k", stage.enemies.count { it == EnemyKind.SHARK || it == EnemyKind.DARK_SHARK } <= 2)
                assertTrue("poziom $n etap $k", stage.enemies.count { it == EnemyKind.STALKER } <= 2)
                assertEquals(stage.enemySlots.filterNotNull(), stage.enemies)
            }
        }
        // Ławica to same piranie.
        for (n in levels) {
            val plan = LevelGenerator.plan(n)
            if (plan.spec.type == LevelType.SWARM) assertEquals(0, stalkers(plan))
        }
    }

    /** Czas wzorcowy (a z nim maksymalny wynik na serwerze) liczony jest z potworów sprzed widmokudy – serwer jej nie zna. */
    @Test
    fun serverLimitsDoNotDependOnStalkers() {
        for (n in (2..999) + (1001..1300)) {
            val plan = LevelGenerator.plan(n)
            val par = if (n > 1000) SeedLevels.parSeconds(plan.stages.map { it.withAllSlots() })
            else ceil(plan.stages.sumOf { it.quota * 2.6 + it.rows * 0.32 + it.enemySlots.size * 0.6 } + 3.0).toInt()
            assertEquals("poziom $n", par, plan.spec.parSeconds)
        }
        for (kind in ChallengeKind.entries) for (i in 0 until 100) {
            val plan = Challenges.plan(kind, LevelSeed(Rng.mix(0x5A1L, i.toLong())))
            assertEquals(SeedLevels.parSeconds(plan.stages.map { it.withAllSlots() }), plan.spec.parSeconds)
        }
    }

    private fun wojtoteka.ovh.fishingparty.level.StagePlan.withAllSlots() = wojtoteka.ovh.fishingparty.level.StagePlan(
        rows, quota, common, blue, golden, enemySlots.map { it ?: EnemyKind.PIRANHA }, rocks, islands, doorCenter, wideBanks,
    )

    /** Na prawdziwych planszach widmokuda nie utyka przy brzegu – w patrolu cały czas płynie. */
    @Test
    fun neverGetsStuckOnRealLevels() {
        var checked = 0
        for (n in (20..120) + (500..520) + (1001..1020)) {
            val full = LevelGenerator.generate(n)
            for (s in full.stages.filter { st -> st.enemies.any { it.kind == EnemyKind.STALKER } }) {
                // Sam ten etap, a łódź stoi w murze pod nim – widmokuda jej nie widzi i tylko patroluje.
                val only = StageLayout(0, s.gateRow, s.topRow, s.bottomRow, s.doorCols, s.quota, s.fish, s.golden, s.enemies, s.reachable)
                val layout = LevelLayout(full.spec, full.grid, listOf(only), WIDTH / 2, s.bottomRow + 1, full.crabs)
                val world = GameWorld(layout, Loadout(), tutorial = false, listener = Hits())
                world.setViewport(180f, 390f)
                val stalkers = world.enemies.filter { it.kind == EnemyKind.STALKER }
                val last = stalkers.map { it.x to it.y }.toMutableList()
                val still = FloatArray(stalkers.size)
                repeat(60 * 20) {
                    world.advance(1f / 60f)
                    assertEquals(Phase.PLAYING, world.phase)
                    for ((i, e) in stalkers.withIndex()) {
                        if (e.harmless || e.hunt != Hunt.PATROL) continue
                        val moved = (e.x to e.y) != last[i]
                        still[i] = if (moved) 0f else still[i] + 1f / 60f
                        last[i] = e.x to e.y
                        assertTrue("poziom $n: widmokuda stoi w miejscu ${still[i]} s (${e.x}, ${e.y})", still[i] < 0.5f)
                    }
                }
                checked++
            }
        }
        assertTrue(checked > 30)
    }

    // ---------------------------------------------------------------- polowanie na prostej planszy

    private class Hits : GameListener {
        override fun onSound(sfx: Sfx) = Unit
        override fun onVibrate(millis: Long) = Unit
        override fun onFinished(result: RunResult, reason: LoseReason?) = Unit
    }

    /**
     * Otwarta woda 11 × 20 pól, widmokuda w górnej części, łódź na dole (ok. 7 pól od niej).
     * [wall] – rząd skał w poprzek planszy między nimi.
     */
    private fun arena(wall: Boolean): GameWorld {
        val height = 24
        val grid = TileGrid(WIDTH, height)
        for (c in 0 until WIDTH) {
            grid[c, 0] = Tile.WALL
            grid[c, 1] = Tile.WALL
            grid[c, height - 1] = Tile.WALL_BOTTOM
            if (wall) grid[c, 14] = Tile.ROCK
        }
        val spec = LevelSpec(
            number = 500, world = 0, type = LevelType.CLASSIC, seed = 42L, secondGoal = SecondGoal.NO_DAMAGE,
            thirdGoal = ThirdGoal.PAR_TIME, parSeconds = 60, timeLimitSeconds = 0, scoreTarget = 100, comboTarget = 3,
            enemySpeed = 1.2f, currentX = 0f, currentY = 0f,
        )
        val stage = StageLayout(
            index = 0, gateRow = 1, topRow = 2, bottomRow = height - 2, doorCols = 4..6, quota = 99,
            fish = emptyList(), golden = emptyList(), enemies = listOf(EnemySpawn(EnemyKind.STALKER, 5, 8)),
            reachable = IntArray(0),
        )
        val layout = LevelLayout(spec, grid, listOf(stage), startCol = 5, startRow = 20, crabs = emptyList())
        return GameWorld(layout, Loadout(extraLife = true), tutorial = false, listener = Hits()).also { it.setViewport(180f, 390f) }
    }

    @Test
    fun cannotSeeThroughRocks() {
        val world = arena(wall = true)
        repeat(60 * 30) {
            world.advance(1f / 60f)
            assertFalse("widmokuda widzi przez skały", world.enemies.single().locked)
        }
        assertEquals(0, world.hits)
    }

    @Test
    fun huntsWhatItSeesWithAWarning() {
        val world = arena(wall = false)
        var lockedAt = -1f
        var aimSeconds = 0f
        repeat(60 * 30) {
            world.advance(1f / 60f)
            val e = world.enemies.single()
            if (e.hunt == Hunt.AIM && lockedAt < 0f) lockedAt = world.elapsed
            if (e.hunt == Hunt.DASH && lockedAt >= 0f && aimSeconds == 0f) aimSeconds = world.elapsed - lockedAt
        }
        assertTrue("nie zauważyła łodzi na otwartej wodzie", lockedAt >= 0f)
        // Zanim skoczy, świeci oczami przez ponad pół sekundy – jest czas na unik.
        assertTrue("za krótkie ostrzeżenie: $aimSeconds s", aimSeconds >= 0.5f)
        // Łódź, która stoi w miejscu, zostaje trafiona.
        assertTrue(world.hits > 0)
    }

    @Test
    fun dodgingSidewaysBeatsStandingStill() {
        fun play(dodge: Boolean): Int {
            val world = arena(wall = false)
            var dodgeLeft = 0f
            var side = 1f
            repeat(60 * 40) {
                val e = world.enemies.single()
                if (dodge && e.hunt == Hunt.AIM && dodgeLeft <= 0f) {
                    dodgeLeft = 0.9f
                    // W bok od linii widmokuda–łódź, w stronę, gdzie jest więcej wody.
                    side = if (world.shipX < WIDTH * TILE / 2f) 1f else -1f
                }
                if (dodgeLeft > 0f) {
                    val dx = world.shipX - e.x
                    val dy = world.shipY - e.y
                    world.setInput(true, atan2(dx * side, -dy * side))
                    dodgeLeft -= 1f / 60f
                } else {
                    world.setInput(false, 0f)
                }
                world.advance(1f / 60f)
            }
            return world.hits
        }
        val standing = play(dodge = false)
        val dodging = play(dodge = true)
        assertTrue("unik: $dodging trafień, bez ruchu: $standing", dodging < standing)
    }

    @Test
    fun fleesAndRestsAfterEveryAttack() {
        val world = arena(wall = false)
        val e = world.enemies.single()
        var attacks = 0
        var prev = e.hunt
        var fleeStart = -1f
        var startDist = 0f
        repeat(60 * 40) {
            world.advance(1f / 60f)
            val dist = kotlin.math.hypot(e.x - world.shipX, e.y - world.shipY)
            if (e.hunt == Hunt.FLEE && prev == Hunt.DASH) {
                attacks++
                fleeStart = world.elapsed
                startDist = dist
            }
            // Po ucieczce jest dalej od łodzi niż po skoku.
            if (prev == Hunt.FLEE && e.hunt == Hunt.PATROL) {
                assertTrue("nie odpłynęła: $startDist → $dist", dist > startDist + 20f)
            }
            // Przerwa: zanim minie, nie namierza łodzi, choć ta stoi na otwartej wodzie.
            if (e.hunt == Hunt.AIM && prev != Hunt.AIM && fleeStart >= 0f) {
                val pause = world.elapsed - fleeStart
                assertTrue("za krótka przerwa: $pause s", pause >= GameWorld.STALKER_FLEE_TIME + GameWorld.STALKER_CALM_TIME - 0.05f)
            }
            prev = e.hunt
        }
        assertTrue("nie zaatakowała", attacks >= 1)
    }
}
