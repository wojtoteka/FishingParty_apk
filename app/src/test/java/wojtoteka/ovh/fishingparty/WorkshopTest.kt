package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.data.Workshop
import wojtoteka.ovh.fishingparty.gfx.HullPaints
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import wojtoteka.ovh.fishingparty.gfx.SailStyles
import java.io.File
import javax.imageio.ImageIO

class WorkshopTest {
    private fun c(rgb: Int) = 0xFF000000.toInt() or rgb

    private val white = c(0xEEEEEE)
    private val red = c(0xDB2C2C)
    private val blue = c(0x2C75DB)
    private val yellow = c(0xDBC42C)

    @Test
    fun presetHullsMatchSpriteAssets() {
        assertEquals(Catalog.boats.map { it.id }.toSet(), HullPaints.presets.keys)
        for (part in listOf("ship", "ship2", "shiptop")) {
            val original = ImageIO.read(File("../tools/original_sprites/$part.png"))
            for (boat in Catalog.boats) {
                val skin = ImageIO.read(File("src/main/assets/sprites/boats/${boat.id}/$part.png"))
                val shades = HullPaints.presets.getValue(boat.id)
                for (y in 0 until original.height) for (x in 0 until original.width) {
                    val k = HullPaints.ORIGINAL.indexOf(original.getRGB(x, y))
                    if (k >= 0) assertEquals("${boat.id}/$part ($x,$y)", HullPaints.hex(shades[k]), HullPaints.hex(skin.getRGB(x, y)))
                }
            }
        }
    }

    @Test
    fun paletteIsAFullGridWithPrices() {
        assertEquals(0, Workshop.palette.size % Workshop.COLUMNS)
        assertEquals(Workshop.palette.size, Workshop.palette.map { it.color }.toSet().size)
        assertTrue(Workshop.palette.all { it.boatPrice > 0 && it.sailPrice > 0 })
        assertNotNull(Workshop.boatItem(Workshop.DEFAULT_BOAT))
        assertNotNull(Workshop.sailItem(Workshop.DEFAULT_SAIL))
    }

    @Test
    fun creatorBoatIsNeverCheaperThanASimilarShopBoat() {
        for (shade in Workshop.palette) {
            val price = Workshop.boatItem(shade.color)!!.price
            assertTrue(price >= shade.boatPrice)
            val hull = Workshop.labs(HullPaints.shades(shade.color))
            for (boat in Catalog.boats) {
                val distance = Workshop.hullDistance(hull, Workshop.labs(HullPaints.presets.getValue(boat.id)))
                if (distance < Workshop.HULL_SIMILAR) assertTrue("${HullPaints.hex(shade.color)} vs ${boat.id}", price > boat.price)
            }
        }
        // Przykłady z życia: czarna jak „mroczna”, szara jak „srebrna”, bursztynowa jak „złota”.
        assertTrue(Workshop.boatItem(c(0x1E1E24))!!.price > 2000)
        assertTrue(Workshop.boatItem(c(0x8C9098))!!.price > 1600)
        assertTrue(Workshop.boatItem(c(0xDB9B2C))!!.price > 3500)
        assertEquals("shadow", Workshop.boatQuote(c(0x1E1E24))!!.similar?.id)
    }

    @Test
    fun creatorSailIsNeverCheaperThanASimilarShopSail() {
        val presets = Catalog.sails.associateWith { Workshop.labGrid(SailStyles.grid(it.id)) }
        for (pattern in SailPattern.entries) for (a in Workshop.palette) for (b in Workshop.palette) {
            if (a === b) continue
            val design = SailDesign(pattern, a.color, b.color)
            val price = Workshop.sailItem(design)!!.price
            assertTrue(price >= Workshop.patternPrice(pattern) + a.sailPrice + b.sailPrice)
            val grid = Workshop.labGrid(design.grid())
            for ((sail, preset) in presets) {
                if (Workshop.sailMatch(grid, preset) >= Workshop.SAIL_MATCH) assertTrue("${design.id} vs ${sail.id}", price > sail.price)
            }
        }
    }

    @Test
    fun copiesOfShopSailsCostMoreThanInTheShop() {
        val cases = listOf(
            SailDesign(SailPattern.HALVES, white, red) to "poland",
            SailDesign(SailPattern.HALVES, blue, yellow) to "ukraine",
            SailDesign(SailPattern.BAND, red, white) to "austria",
            SailDesign(SailPattern.CROSS, blue, yellow) to "sweden",
            SailDesign(SailPattern.CROSS, red, white) to "denmark",
            SailDesign(SailPattern.STRIPES, white, red) to "striped",
            SailDesign(SailPattern.STRIPES, yellow, c(0xF7EA94)) to "gold",
        )
        for ((design, id) in cases) {
            val shop = Catalog.sails.first { it.id == id }
            val quote = Workshop.sailQuote(design)!!
            assertTrue("${design.id} ~ $id", quote.price > shop.price)
            assertTrue("${design.id} ~ $id", quote.similar!!.price >= shop.price)
            // Odbity żagiel (łódź płynie w drugą stronę) to ten sam wygląd.
            if (design.pattern == SailPattern.CROSS) {
                val mirrored = Workshop.sailMatch(Workshop.labGrid(design.grid().map { it.reversedArray() }.toTypedArray()), Workshop.labGrid(SailStyles.grid(id)))
                assertTrue(mirrored >= Workshop.SAIL_MATCH)
            }
        }
    }

    @Test
    fun idsRoundTripAndRejectGarbage() {
        val design = SailDesign(SailPattern.CHECKER, white, blue)
        assertEquals("own-checker-EEEEEE-2C75DB", design.id)
        assertEquals(design, SailDesign.parse(design.id))
        assertEquals(blue, HullPaints.ownColor(HullPaints.ownId(blue)))
        assertNull(SailDesign.parse("own-laser-EEEEEE-2C75DB"))
        assertNull(SailDesign.parse("own-checker-eeeeee-2C75DB"))
        assertNull(SailDesign.parse("own-checker-EEEEEE"))
        assertNull(HullPaints.ownColor("own-12345"))
        assertNull(HullPaints.ownColor("classic"))
        // Kolor spoza palety i żagiel z dwóch takich samych kolorów nie są rzeczami ze sklepu.
        assertNull(Catalog.find("boat:own-123456"))
        assertNull(Catalog.find("sail:own-halves-EEEEEE-EEEEEE"))
        assertNull(Workshop.find("sailor:own-EEEEEE"))
        assertEquals(Workshop.boatItem(blue)!!.price, Catalog.find("boat:own-2C75DB")!!.price)
        // Projekt z kreatora rysuje się z własnych pasów, a nie jako biały żagiel.
        assertEquals(blue, SailStyles.bands(SailDesign(SailPattern.HALVES, white, blue).id)[1][0])
        assertEquals(white, SailStyles.bands(SailDesign(SailPattern.HALVES, white, blue).id)[9][0])
    }

    @Test
    fun boughtCreationsAreSavedAndEquipped() {
        val save = SaveData()
        val p = Progress(save, { 1_000_000_000L }, { 20_000L })
        val boat = Workshop.boatItem(blue)!!
        val sail = Workshop.sailItem(SailDesign(SailPattern.DIAGONAL, white, red))!!
        assertFalse(p.buy(boat))
        p.addCoins((boat.price + sail.price).toLong())
        assertTrue(p.buy(boat))
        assertTrue(p.buy(sail))
        assertEquals(0L, p.coins)
        assertTrue(p.isEquipped(boat) && p.isEquipped(sail))
        assertEquals(listOf(boat.key), p.creations(ItemKind.BOAT).map { it.key })
        assertEquals(listOf(sail.key), p.creations(ItemKind.SAIL).map { it.key })

        val copy = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(boat.id, copy.boat)
        assertEquals(sail.id, copy.sail)
        assertTrue(boat.key in copy.owned && sail.key in copy.owned)

        // Wybór innej łodzi i powrót do własnej – jak w sklepie.
        p.equip(Catalog.boats.first())
        assertFalse(p.isEquipped(boat))
        p.equip(boat)
        assertTrue(p.isEquipped(boat))
    }
}
