package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.EndlessLevels
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelLayout
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.Worlds
import java.security.MessageDigest
import kotlin.math.abs

/** Bezkresne Morze: poziomy 1001+ z seeda muszą być grywalne, różne i w miarę trudne. */
class EndlessLevelsTest {

    private val first = LevelConstants.LEVEL_COUNT + 1
    private val range = first until first + 2000

    /** Pierwsze 2000 poziomów i próbki dalekiego Bezkresnego Morza (okolice 6000, 10 000 i sam koniec). */
    private val samples = range + (5901..6100) + (9901..10100) + (LevelConstants.MAX_LEVEL - 199..LevelConstants.MAX_LEVEL)
    private val all: List<LevelLayout> = samples.map { LevelGenerator.generate(it) }

    @Test
    fun seedCodeRoundTrips() {
        val rng = Rng(42)
        repeat(500) {
            val seed = LevelSeed(rng.nextLong())
            assertEquals(seed, LevelSeed.parse(seed.code))
            assertEquals(seed, LevelSeed.parse(seed.code.lowercase().replace("-", " ")))
        }
        assertEquals(LevelSeed(0), LevelSeed.parse("0000-0000-0000-0"))
        assertEquals(LevelSeed(-1), LevelSeed.parse("FZZZ-ZZZZ-ZZZZ-Z"))
        assertEquals(null, LevelSeed.parse("GZZZ-ZZZZ-ZZZZ-Z"))
        assertEquals(null, LevelSeed.parse("ABC"))
        assertEquals(null, LevelSeed.parse("0000-0000-0000-U"))
    }

    @Test
    fun everyLevelHasOwnSeedAndOwnLayout() {
        val seeds = (first..LevelConstants.MAX_LEVEL).map { EndlessLevels.seedOf(it).value }
        assertEquals(seeds.size, seeds.toSet().size)
        // Żadna plansza się nie powtarza.
        val prints = all.map { fingerprint(it) }
        assertEquals(prints.size, prints.toSet().size)
        // Nie ma wspólnego seeda z kampanią.
        val campaign = (1..LevelConstants.LEVEL_COUNT).map { LevelGenerator.seedOf(it) }.toSet()
        assertTrue(seeds.none { it in campaign })
    }

    @Test
    fun generationIsDeterministic() {
        for (n in listOf(first, 1337, 5000, LevelConstants.MAX_LEVEL)) {
            assertEquals(fingerprint(LevelGenerator.generate(n)), fingerprint(LevelGenerator.generate(n)))
        }
    }

    @Test
    fun layoutsArePlayable() {
        for (layout in all) {
            val g = layout.grid
            val n = layout.spec.number
            val reach = LevelGenerator.reachableFromStart(g, layout.startCol, layout.startRow)
            for (r in 1 until g.height - 1) for (c in 0 until g.width) {
                val t = g[c, r]
                if (t == Tile.SEA) assertTrue("poziom $n: woda bez dojścia $c,$r", reach[r * g.width + c])
                if (t == Tile.SAND) {
                    val no = water(g[c, r - 1]) && water(g[c, r + 1]) || water(g[c - 1, r]) && water(g[c + 1, r])
                    assertTrue("poziom $n: piasek bez sprite'a $c,$r", !no)
                }
            }
            val finish = (1 until LevelConstants.FINISH_ROWS).any { r -> (0 until g.width).any { c -> reach[r * g.width + c] } }
            assertTrue("poziom $n: meta nieosiągalna", finish)
            for (stage in layout.stages) {
                assertTrue("poziom $n: za mało ryb", stage.fish.size >= stage.quota)
                // Na etapie musi być dość wody na ryby, potwory i manewry łodzią.
                assertTrue("poziom $n: ciasny etap ${stage.reachable.size}", stage.reachable.size >= stage.fish.size + stage.enemies.size + 12)
                for (f in stage.fish) assertEquals(Tile.SEA, g[f.col, f.row])
                for (e in stage.enemies) assertEquals(Tile.SEA, g[e.col, e.row])
                for (c in stage.doorCols) assertEquals(Tile.DOOR, g[c, stage.gateRow])
            }
        }
    }

    @Test
    fun difficultyIsBetweenEarlyGameAndFinale() {
        fun avgEnemies(r: IntRange) = r.sumOf { LevelGenerator.plan(it).enemyCount } / r.count().toFloat()
        fun avgStages(r: IntRange) = r.sumOf { LevelGenerator.plan(it).stages.size } / r.count().toFloat()
        val early = avgEnemies(2..50)
        val firstVoyageEnd = avgEnemies(451..500)
        val secondVoyageEnd = avgEnemies(950..999)
        val endless = avgEnemies(range)
        assertTrue("za łatwo: $endless vs $firstVoyageEnd", endless > early * 2 && endless > firstVoyageEnd * 0.8f)
        assertTrue("za trudno: $endless vs $secondVoyageEnd", endless < secondVoyageEnd)
        assertTrue(avgStages(range) <= avgStages(950..999))
        assertTrue(range.all { LevelGenerator.plan(it).stages.size >= 3 })
    }

    @Test
    fun farLevelsAreAsHardAsTheFirstOnes() {
        fun avgEnemies(r: IntRange) = r.sumOf { LevelGenerator.plan(it).enemyCount } / r.count().toFloat()
        val near = avgEnemies(1001..1200)
        for (far in listOf(5901..6100, 9901..10100, LevelConstants.MAX_LEVEL - 199..LevelConstants.MAX_LEVEL)) {
            val avg = avgEnemies(far)
            assertTrue("$far: $avg vs $near", abs(avg - near) < near * 0.15f)
        }
    }

    @Test
    fun levelsStayFairAndVaried() {
        val types = mutableSetOf<LevelType>()
        var previous: LevelType? = null
        for (n in samples) {
            if (n - 1 !in samples) previous = null
            val plan = LevelGenerator.plan(n)
            val spec = plan.spec
            types += spec.type
            assertEquals(LevelGenerator.isBoss(n), spec.type == LevelType.BOSS)
            assertTrue(spec.type != LevelType.FINALE)
            if (spec.type != LevelType.CLASSIC && spec.type != LevelType.BOSS) assertNotEquals("poziom $n", previous, spec.type)
            previous = spec.type
            assertTrue(spec.comboTarget <= LevelConstants.MAX_COMBO)
            assertTrue("poziom $n: potwory ${spec.enemySpeed}", spec.enemySpeed < 1.45f)
            assertTrue(kotlin.math.abs(spec.currentX) + kotlin.math.abs(spec.currentY) < 30f)
            assertTrue(plan.stages.all { it.enemies.size <= 10 && it.rows <= 24 })
            assertEquals(LevelGenerator.worldOf(n), spec.world)
        }
        assertEquals(LevelType.entries.toSet() - LevelType.FINALE, types)
    }

    @Test
    fun endlessWorldsChangePaletteAndName() {
        for (w in 1 until 400) {
            assertNotEquals("świat $w", Worlds.endlessPalette(w - 1), Worlds.endlessPalette(w))
            val (a, b) = Worlds.endlessName(w)
            val (pa, pb) = Worlds.endlessName(w - 1)
            assertTrue(a != pa && b != pb)
            assertTrue(a in 0 until Worlds.ENDLESS_NAME_A && b in 0 until Worlds.ENDLESS_NAME_B)
        }
        // W każdej dziesiątce światów każda paleta raz.
        for (block in 0 until 20) assertEquals(10, (0 until 10).map { Worlds.endlessPalette(block * 10 + it) }.toSet().size)
        assertEquals(Worlds.FINALE_THEME, Worlds.theme(Worlds.FINALE))
        assertTrue(Worlds.theme(LevelConstants.WORLD_COUNT) in Worlds.THEMES)
    }

    private fun water(t: Tile) = t == Tile.SEA || t == Tile.ROCK

    private fun fingerprint(layout: LevelLayout): String {
        val md = MessageDigest.getInstance("SHA-256")
        val g = layout.grid
        for (r in 0 until g.height) for (c in 0 until g.width) md.update(g[c, r].ordinal.toByte())
        for (st in layout.stages) {
            for (f in st.fish) md.update("f${f.kind}${f.col},${f.row}".toByteArray())
            for (e in st.enemies) md.update("e${e.kind}${e.col},${e.row}".toByteArray())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
