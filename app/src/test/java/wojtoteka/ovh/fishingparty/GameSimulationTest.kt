package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Phase
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelLayout
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import wojtoteka.ovh.fishingparty.level.StageLayout
import wojtoteka.ovh.fishingparty.level.Tile
import kotlin.math.atan2
import kotlin.math.floor

/**
 * Bot przechodzi poziomy w prawdziwej symulacji (bez Androida): płynie do
 * najbliższej ryby, a po otwarciu bramy – do wrót. Przeciwników usuwamy,
 * bo sprawdzamy tu przejezdność i logikę etapów, nie refleks.
 */
class GameSimulationTest {

    private class Recorder : GameListener {
        var result: RunResult? = null
        var reason: LoseReason? = null
        override fun onSound(sfx: Sfx) = Unit
        override fun onVibrate(millis: Long) = Unit
        override fun onFinished(result: RunResult, reason: LoseReason?) {
            this.result = result
            this.reason = reason
        }
    }

    private fun withoutEnemies(layout: LevelLayout) = LevelLayout(
        spec = layout.spec,
        grid = layout.grid,
        stages = layout.stages.map {
            StageLayout(it.index, it.gateRow, it.topRow, it.bottomRow, it.doorCols, it.quota, it.fish, it.golden, emptyList(), it.reachable)
        },
        startCol = layout.startCol,
        startRow = layout.startRow,
        crabs = layout.crabs,
    )

    private fun play(number: Int): Pair<RunResult?, GameWorld> {
        val layout = withoutEnemies(LevelGenerator.generate(number))
        val recorder = Recorder()
        val world = GameWorld(layout, Loadout(), tutorial = false, listener = recorder)
        world.setViewport(180f, 390f)
        val frame = 1f / 60f
        var t = 0f
        while (recorder.result == null && t < 600f) {
            steer(world)
            world.advance(frame)
            t += frame
        }
        return recorder.result to world
    }

    private fun steer(world: GameWorld) {
        val grid = world.grid
        val stage = world.currentStage
        val sc = floor(world.shipX / TILE).toInt()
        val sr = floor(world.shipY / TILE).toInt()
        val targets = HashSet<Int>()
        if (!world.isDoorOpen(world.stageIndex)) {
            world.fish.filter { it.stage == world.stageIndex }.forEach {
                targets += floor(it.y / TILE).toInt() * grid.width + floor(it.x / TILE).toInt()
            }
        } else {
            for (c in stage.doorCols) targets += (stage.gateRow - 1) * grid.width + c
        }
        if (targets.isEmpty()) {
            world.setInput(false, 0f)
            return
        }
        // BFS po polach wody od łodzi do najbliższego celu.
        val prev = IntArray(grid.width * grid.height) { -2 }
        val start = sr * grid.width + sc
        val queue = ArrayDeque<Int>()
        queue += start
        prev[start] = -1
        var found = -1
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell in targets) {
                found = cell; break
            }
            val c = cell % grid.width
            val r = cell / grid.width
            for ((dc, dr) in listOf(0 to -1, 1 to 0, -1 to 0, 0 to 1)) {
                val nc = c + dc
                val nr = r + dr
                if (!grid.inside(nc, nr)) continue
                val t = grid[nc, nr]
                val passable = t == Tile.SEA || (t == Tile.DOOR && world.isDoorOpen(world.stageOfGateRow(nr)))
                val idx = nr * grid.width + nc
                if (passable && prev[idx] == -2) {
                    prev[idx] = cell
                    queue += idx
                }
            }
        }
        if (found < 0) {
            world.setInput(false, 0f)
            return
        }
        var next = found
        while (prev[next] != start && prev[next] >= 0) next = prev[next]
        val tx: Float
        val ty: Float
        if (next == found && found != start && world.isDoorOpen(world.stageIndex)) {
            // przy wrotach płyniemy prosto w górę przez bramę
            tx = (found % grid.width) * TILE + TILE / 2f
            ty = stage.gateRow * TILE - 20f
        } else if (found == start && world.isDoorOpen(world.stageIndex)) {
            tx = world.shipX
            ty = world.shipY - 30f
        } else if (found == start) {
            val f = world.fish.minByOrNull { (it.x - world.shipX) * (it.x - world.shipX) + (it.y - world.shipY) * (it.y - world.shipY) }
            tx = f?.x ?: world.shipX
            ty = f?.y ?: (world.shipY - 20f)
        } else {
            tx = (next % grid.width) * TILE + TILE / 2f
            ty = (next / grid.width) * TILE + TILE / 2f
        }
        world.setInput(true, atan2(ty - world.shipY, tx - world.shipX))
    }

    @Test
    fun botCompletesEarlyLevels() {
        for (n in 1..30) {
            val (result, world) = play(n)
            assertTrue("poziom $n nieukończony (etap ${world.stageIndex}, faza ${world.phase})", result?.completed == true)
            assertEquals(Phase.WON, world.phase)
            assertTrue(result!!.stars >= 1)
            assertTrue("poziom $n: wynik niewiarygodny", ScoreValidator.isPlausible(LevelGenerator.plan(n), result))
        }
    }

    @Test
    fun botCompletesSampledLaterLevels() {
        for (n in listOf(50, 75, 99, 100, 150, 175, 200, 225, 260, 300, 333, 375, 400, 425, 450, 475, 499, 500)) {
            val (result, world) = play(n)
            assertTrue("poziom $n nieukończony (etap ${world.stageIndex}/${world.stages.size})", result?.completed == true)
        }
    }

    @Test
    fun forgedScoreIsRejected() {
        val plan = LevelGenerator.plan(10)
        val (result, _) = play(10)
        val real = result!!
        assertTrue(ScoreValidator.isPlausible(plan, real))
        val forged = RunResult(
            real.level, true, 1_000_000_000, 3, booleanArrayOf(true, true, true), real.timeMillis,
            real.commonCaught, real.blueCaught, real.goldenCaught, 0, 5, real.stagesCleared,
        )
        assertFalse(ScoreValidator.isPlausible(plan, forged))
        val tooFast = RunResult(
            real.level, true, real.score, real.stars, real.starFlags, 50,
            real.commonCaught, real.blueCaught, real.goldenCaught, 0, real.maxCombo, real.stagesCleared,
        )
        assertFalse(ScoreValidator.isPlausible(plan, tooFast))
    }
}
