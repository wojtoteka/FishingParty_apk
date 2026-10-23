package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.data.SaveMerge
import wojtoteka.ovh.fishingparty.data.UserLevelPlay
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.Piece
import wojtoteka.ovh.fishingparty.level.PieceRef
import wojtoteka.ovh.fishingparty.level.Placement
import wojtoteka.ovh.fishingparty.level.UserLevelEdits
import wojtoteka.ovh.fishingparty.level.UserLevels

class UserLevelEditsTest {

    private val start = UserLevels.starter("Edycje")

    @Test
    fun placeTogglesAndReplaces() {
        val shark = UserLevelEdits.place(start, 0, 6, 6, Placement.Enemy(EnemyKind.SHARK))!!
        assertEquals(1, shark.stages[0].enemies.size)
        // Ta sama postać na tym samym polu – usunięta; inna – podmieniona.
        assertTrue(UserLevelEdits.place(shark, 0, 6, 6, Placement.Enemy(EnemyKind.SHARK))!!.stages[0].enemies.isEmpty())
        val fish = UserLevelEdits.place(shark, 0, 6, 6, Placement.Fish(true))!!
        assertTrue(fish.stages[0].enemies.isEmpty())
        assertTrue(fish.stages[0].fish.any { it.blue && it.col == 6 && it.row == 6 })
        // Ryby nie stawia się na piasku, a kraba – na wodzie.
        assertNull(UserLevelEdits.place(start, 0, 0, 5, Placement.Fish(false)))
        assertNull(UserLevelEdits.place(start, 0, 5, 5, Placement.Crab))
        assertNotNull(UserLevelEdits.place(start, 0, 0, 5, Placement.Crab))
    }

    @Test
    fun limitsAreKept() {
        var level = start
        for (i in 0 until UserLevels.MAX_GOLDEN) level = UserLevelEdits.place(level, 0, 2 + i, 2, Placement.Golden)!!
        assertNull(UserLevelEdits.place(level, 0, 2, 3, Placement.Golden))
        // Złote ryby dostają rosnące opóźnienia.
        assertEquals(listOf(3, 12, 21, 30, 39, 48), level.stages[0].golden.map { it.delay })
    }

    @Test
    fun paintSkipsLockedCellsAndErases() {
        val sand = UserLevelEdits.paint(start, 0, listOf(5 to 0, 5 to 1, 4 to 6), UserLevels.SAND)
        assertEquals(UserLevels.SEA, sand.stages[0].cell(5, 0))
        assertEquals(UserLevels.SAND, sand.stages[0].cell(4, 6))
        val erased = UserLevelEdits.paint(start, 0, listOf(3 to 4), UserLevels.SEA)
        assertTrue(erased.stages[0].fish.none { it.col == 3 && it.row == 4 })
        assertEquals(start, UserLevelEdits.paint(start, 0, listOf(5 to 6), UserLevels.SEA))
    }

    @Test
    fun moveAcrossStages() {
        val two = UserLevelEdits.addStage(start, 1)!!
        val ref = UserLevelEdits.pieceAt(two, 0, 3, 4)!!
        val moved = UserLevelEdits.move(two, ref, 1, 5, 8)!!
        assertEquals(3, moved.stages[0].fish.size)
        assertTrue(moved.stages[1].fish.any { it.col == 5 && it.row == 8 })
        // Na zajęte pole i na piasek nie da się przenieść.
        assertNull(UserLevelEdits.move(two, ref, 0, 7, 7))
        assertNull(UserLevelEdits.move(two, ref, 0, 0, 7))
    }

    @Test
    fun rowsGrowAndShrinkFromTop() {
        val taller = UserLevelEdits.setRows(start, 0, 20)
        assertEquals(20, taller.stages[0].rows)
        assertTrue(taller.stages[0].fish.any { it.col == 3 && it.row == 8 })
        val shorter = UserLevelEdits.setRows(start, 0, 10)
        // Ryba z górnych 6 rzędów znika, reszta przesuwa się w górę.
        assertEquals(listOf(1 to 7, 4 to 5, 6 to 4), shorter.stages[0].fish.map { it.row to it.col })
        assertEquals(UserLevels.MIN_ROWS, UserLevelEdits.setRows(start, 0, 1).stages[0].rows)
    }

    @Test
    fun stageOperations() {
        val withShark = UserLevelEdits.place(start, 0, 2, 3, Placement.Enemy(EnemyKind.SHARK))!!.let {
            UserLevelEdits.setEnemyDir(it, PieceRef(0, Piece.ENEMY, 0), 0)
        }
        val mirrored = UserLevelEdits.mirrorStage(withShark.copy(stages = listOf(withShark.stages[0].copy(door = 2, currentX = 10))), 0)
        val s = mirrored.stages[0]
        assertEquals(8, s.door)
        assertEquals(-10, s.currentX)
        assertEquals(8, s.enemies[0].col)
        assertEquals(2, s.enemies[0].dir)
        val dup = UserLevelEdits.duplicateStage(start, 0)!!
        assertEquals(2, dup.stages.size)
        assertEquals(UserLevels.encode(dup.copy(stages = listOf(dup.stages[0]))), UserLevels.encode(start))
        assertNull(UserLevelEdits.deleteStage(start, 0))
        val swapped = UserLevelEdits.moveStage(UserLevelEdits.addStage(start, 1)!!, 0, up = true)!!
        assertTrue(swapped.stages[0].fish.isEmpty())
        assertTrue(UserLevelEdits.clearStage(start, 0).stages[0].fish.isEmpty())
        // Piranie nie dostaną kierunku w pionie.
        val piranha = UserLevelEdits.place(start, 0, 6, 9, Placement.Enemy(EnemyKind.PIRANHA))!!
        val ref = UserLevelEdits.pieceAt(piranha, 0, 6, 9)!!
        assertEquals(-1, UserLevelEdits.setEnemyDir(piranha, ref, 1).stages[0].enemies[0].dir)
    }

    @Test
    fun movingStageThereAndBackKeepsSandAtStart() {
        // Etap 2 z wyspą 3×3 na dole, na środku – na starcie (etap 1) te pola są zawsze wodą.
        val c = UserLevels.START_COL
        val two = UserLevelEdits.addStage(start.copy(stages = listOf(start.stages[0].copy(door = 2))), 1)!!
        val rows = two.stages[1].rows
        val island = (rows - 3 until rows).flatMap { r -> (c - 1..c + 1).map { it to r } }
        val raw = UserLevels.normalize(UserLevelEdits.paint(two, 1, island, UserLevels.SAND))
        assertEquals(UserLevels.SAND, raw.stages[1].cell(c, rows - 2))
        val down = UserLevelEdits.moveStage(raw, 1, up = false)!!
        val shown = UserLevels.normalize(down)
        assertEquals(UserLevels.SEA, shown.stages[0].cell(c, rows - 2))
        // Zmiana w innym etapie nie gubi ukrytego piasku – po powrocie wyspa jest na miejscu.
        val edited = UserLevelEdits.keepLocked(UserLevelEdits.setQuota(shown, 1, 2), shown, down)
        val back = UserLevels.normalize(UserLevelEdits.moveStage(edited, 0, up = true)!!)
        assertTrue(island.all { (col, row) -> back.stages[1].cell(col, row) == UserLevels.SAND })
        assertEquals(2, back.stages[0].quota)
    }

    @Test
    fun saveKeepsUserLevelProgress() {
        val d = SaveData()
        d.userLevelsDone += listOf(5, 9, 12)
        d.userLevelQueue += UserLevelPlay(9, true, 640, 31_000, 4, 1, 0, 0, 3, 2, 1_700_000_000_000)
        d.marketClaim = "ab".repeat(16)
        val copy = SaveCodec.decode(SaveCodec.encode(d))
        assertEquals(setOf(5, 9, 12), copy.userLevelsDone)
        assertEquals(640, copy.userLevelQueue.single().score)
        assertEquals(d.marketClaim, copy.marketClaim)
        // Dwa telefony: ukończone poziomy i kolejki się sumują, bez powtórek tego samego przejazdu.
        val base = SaveCodec.decode(SaveCodec.encode(d))
        val remote = SaveCodec.decode(SaveCodec.encode(d)).apply {
            userLevelsDone += 77
            userLevelQueue += UserLevelPlay(77, false, 10, 5_000, 1, 0, 0, 1, 1, 0, 1_700_000_100_000)
            marketClaim = ""
        }
        val merged = SaveMerge.merge(base, d, remote)
        assertEquals(setOf(5, 9, 12, 77), merged.userLevelsDone)
        assertEquals(2, merged.userLevelQueue.size)
        assertEquals(d.marketClaim, merged.marketClaim)
    }
}
