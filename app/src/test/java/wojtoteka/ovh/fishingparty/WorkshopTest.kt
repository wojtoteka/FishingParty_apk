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
import wojtoteka.ovh.fishingparty.gfx.ColorMath
import wojtoteka.ovh.fishingparty.gfx.Hsv
import wojtoteka.ovh.fishingparty.gfx.HullPaints
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import wojtoteka.ovh.fishingparty.gfx.SailStyles
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot

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
    fun everyColorFallsIntoAPricedGroup() {
        assertEquals(Workshop.Hue.entries.size * Workshop.Tone.entries.size + Workshop.Neutral.entries.size, Workshop.groups.size)
        assertTrue(Workshop.groups.all { it.boatPrice > 0 && it.sailPrice > 0 })
        // Typowy kolor każdej grupy należy do niej samej (cennik pokazuje prawdziwe ceny).
        for (g in Workshop.groups) assertTrue(Workshop.groupOf(g.sample.color) === g)
        fun group(rgb: Int) = Workshop.groupOf(c(rgb))
        assertEquals(Workshop.Neutral.BLACK, group(0x000000).neutral)
        assertEquals(Workshop.Neutral.BLACK, group(0x200808).neutral)
        assertEquals(Workshop.Neutral.WHITE, group(0xFFFFFF).neutral)
        assertEquals(Workshop.Neutral.GREY, group(0x8C9098).neutral)
        assertEquals(Workshop.Hue.RED to Workshop.Tone.VIVID, group(0xDB2C2C).let { it.hue to it.tone })
        assertEquals(Workshop.Hue.RED to Workshop.Tone.PASTEL, group(0xF79494).let { it.hue to it.tone })
        assertEquals(Workshop.Hue.RED to Workshop.Tone.DARK, group(0x801313).let { it.hue to it.tone })
        assertEquals(Workshop.Hue.RED, group(0xE0204A).hue) // 345° – czerwień przechodzi przez 0°
        assertEquals(Workshop.Hue.BLUE, group(0x2C75DB).hue)
        assertEquals(Workshop.Hue.YELLOW, group(0xDBC42C).hue)
        // Każdy z 16 mln kolorów (próbka co 15 na kanał) ma grupę.
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) Workshop.groupOf(HullPaints.parseHex("%02X%02X%02X".format(r, g, b))!!)
        assertNotNull(Workshop.sailItem(SailDesign(Workshop.DEFAULT_SAIL_PATTERN, Workshop.DEFAULT_SAIL_FIRST.color, Workshop.DEFAULT_SAIL_SECOND.color)))
    }

    /** Siatka kolorów z całej palety: barwa co 6°, nasycenie i jasność co 0,1. */
    private val sweep: List<Int> = buildList {
        for (h in 0 until 60) for (s in 0..10) for (v in 1..10) add(Hsv(h / 60f, s / 10f, v / 10f).color)
    }.distinct()

    @Test
    fun creatorBoatIsNeverCheaperThanASimilarShopBoat() {
        val presets = Catalog.boats.associateWith { Workshop.labs(HullPaints.presets.getValue(it.id)) }
        for (color in sweep) {
            val price = Workshop.boatItem(color).price
            assertTrue(price >= Workshop.groupOf(color).boatPrice)
            val hull = Workshop.labs(HullPaints.shades(color))
            for ((boat, preset) in presets) {
                if (Workshop.hullDistance(hull, preset) < Workshop.HULL_SIMILAR) assertTrue("${HullPaints.hex(color)} vs ${boat.id}", price > boat.price)
            }
        }
        // Kolory dokładnie takie jak łodzie ze sklepu (kolor główny kadłuba) są zawsze droższe.
        for (boat in Catalog.boats) {
            val main = HullPaints.presets.getValue(boat.id)[1]
            assertTrue(boat.id, Workshop.boatItem(main).price > boat.price)
        }
        // Kolor z grupy czerni zawsze droższy niż „mroczna”, z szarości – niż „srebrna”, bez względu na odcień.
        for (color in sweep) {
            val group = Workshop.groupOf(color).neutral
            if (group == Workshop.Neutral.BLACK) assertTrue(HullPaints.hex(color), Workshop.boatItem(color).price > 2000)
            if (group == Workshop.Neutral.GREY) assertTrue(HullPaints.hex(color), Workshop.boatItem(color).price > 1600)
        }
        // Prawie czarne kolory z barwą (granatowa, bordowa, zielona czerń) to też czerń – nie da się nimi obejść ceny.
        for (rgb in listOf(0x000033, 0x00002D, 0x031240, 0x2D0000, 0x00260A, 0x1A0A2E, 0x2C2C34)) {
            assertEquals(HullPaints.hex(rgb), Workshop.Neutral.BLACK, Workshop.groupOf(c(rgb)).neutral)
            assertTrue(HullPaints.hex(rgb), Workshop.boatItem(c(rgb)).price > 2000)
        }
        // Kolory, które wyglądają na białe (bardzo jasne i prawie bez barwy, także lekko różowe czy niebieskawe),
        // nie są tańsze niż „lodowa” – najbliższa bieli łódź ze sklepu.
        val ice = Catalog.boats.first { it.id == "ice" }.price
        for (h in 0 until 72) for (s in 0..20) for (v in 85..100) {
            val color = Hsv(h / 72f, s / 100f, v / 100f).color
            val lab = ColorMath.lab(color)
            if (lab[0] >= 90f && hypot(lab[1], lab[2]) <= 15f) assertTrue(HullPaints.hex(color), Workshop.boatItem(color).price > ice)
        }
        // …ale wyraźny granat to nadal niebieski, nie czerń.
        assertEquals(Workshop.Hue.BLUE, Workshop.groupOf(c(0x000080)).hue)
        // Przykłady z życia: czarna jak „mroczna”, szara jak „srebrna”, bursztynowa jak „złota”.
        assertTrue(Workshop.boatItem(c(0x1E1E24)).price > 2000)
        assertTrue(Workshop.boatItem(c(0x8C9098)).price > 1600)
        assertTrue(Workshop.boatItem(c(0xDB9B2C)).price > 3500)
        assertEquals("shadow", Workshop.boatQuote(c(0x1E1E24)).similar?.id)
    }

    @Test
    fun creatorSailIsNeverCheaperThanASimilarShopSail() {
        val presets = Catalog.sails.associateWith { Workshop.swatchGrid(SailStyles.grid(it.id)) }
        // Kolory z flag i żagli sklepu oraz losowe z palety – w każdym wzorze.
        val shopColors = Catalog.sails.flatMap { s -> SailStyles.grid(s.id).flatMap { it.toList() } }.distinct()
        val random = java.util.Random(7)
        val colors = (shopColors + List(40) { sweep[random.nextInt(sweep.size)] }).distinct()
        var checked = 0
        for (pattern in SailPattern.entries) for (a in colors) for (b in colors) {
            if (random.nextInt(8) != 0) continue
            val design = SailDesign(pattern, a, b)
            val price = Workshop.sailItem(design)?.price ?: continue
            checked++
            assertTrue(price >= Workshop.patternPrice(pattern) + Workshop.groupOf(a).sailPrice + Workshop.groupOf(b).sailPrice)
            val grid = Workshop.swatchGrid(design.grid())
            for ((sail, preset) in presets) {
                if (Workshop.sailMatch(grid, preset) >= Workshop.SAIL_MATCH) assertTrue("${design.id} vs ${sail.id}", price > sail.price)
            }
        }
        assertTrue(checked > 3000)
    }

    @Test
    fun copiesOfShopSailsCostMoreThanInTheShop() {
        val cases = listOf(
            SailDesign(SailPattern.HALVES, white, red) to "poland",
            SailDesign(SailPattern.HALVES, c(0xFFE6E6), red) to "poland", // lekko różowa biel to wciąż biel
            SailDesign(SailPattern.HALVES, blue, yellow) to "ukraine",
            SailDesign(SailPattern.BAND, red, white) to "austria",
            SailDesign(SailPattern.CROSS, blue, yellow) to "sweden",
            SailDesign(SailPattern.CROSS, red, white) to "denmark",
            SailDesign(SailPattern.STRIPES, white, red) to "striped",
            SailDesign(SailPattern.STRIPES, yellow, c(0xF7EA94)) to "gold",
            // Dwie czernie (granatowa i grafitowa) to wciąż czarny żagiel.
            SailDesign(SailPattern.DOTS, c(0x000033), c(0x2C2C34)) to "black",
        )
        for ((design, id) in cases) {
            val shop = Catalog.sails.first { it.id == id }
            val quote = Workshop.sailQuote(design)!!
            assertTrue("${design.id} ~ $id", quote.price > shop.price)
            assertTrue("${design.id} ~ $id", quote.similar!!.price >= shop.price)
            // Odbity żagiel (łódź płynie w drugą stronę) to ten sam wygląd.
            if (design.pattern == SailPattern.CROSS) {
                val mirrored = Workshop.sailMatch(Workshop.swatchGrid(design.grid().map { it.reversedArray() }.toTypedArray()), Workshop.swatchGrid(SailStyles.grid(id)))
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
        // Dowolny kolor z palety jest poprawny; żagiel z dwóch (prawie) takich samych kolorów – nie.
        assertEquals(Workshop.boatItem(c(0x123456)).price, Catalog.find("boat:own-123456")!!.price)
        assertNull(Catalog.find("sail:own-halves-EEEEEE-EEEEEE"))
        assertNull(Catalog.find("sail:own-halves-EEEEEE-EBEBEB"))
        assertNotNull(Catalog.find("sail:own-halves-EEEEEE-2C75DB"))
        assertNull(Workshop.find("sailor:own-EEEEEE"))
        assertEquals("own-123456", Catalog.knownOr(ItemKind.BOAT, "own-123456", "classic"))
        assertEquals("classic", Catalog.knownOr(ItemKind.BOAT, "own-12345G", "classic"))
        // Projekt z kreatora rysuje się z własnych pasów, a nie jako biały żagiel.
        assertEquals(blue, SailStyles.bands(SailDesign(SailPattern.HALVES, white, blue).id)[1][0])
        assertEquals(white, SailStyles.bands(SailDesign(SailPattern.HALVES, white, blue).id)[9][0])
    }

    @Test
    fun boughtCreationsAreSavedAndEquipped() {
        val save = SaveData()
        val p = Progress(save, { 1_000_000_000L }, { 20_000L })
        val boat = Workshop.boatItem(blue)
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
