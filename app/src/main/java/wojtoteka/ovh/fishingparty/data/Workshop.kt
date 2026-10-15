package wojtoteka.ovh.fishingparty.data

import wojtoteka.ovh.fishingparty.gfx.HullPaints
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import wojtoteka.ovh.fishingparty.gfx.SailStyles
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Kreator wyglądu w sklepie: własny kolor łodzi i dwukolorowy żagiel z palety odcieni.
 * Każdy odcień i każdy wzór ma swoją cenę. Projekt podobny do czegoś ze sklepu kosztuje
 * zawsze więcej niż ta rzecz – kreator nie jest tańszą drogą do tego samego wyglądu.
 */
object Workshop {
    /** Odcień palety: cena jako kolor łodzi i jako jeden z kolorów żagla. */
    class Shade(val color: Int, val boatPrice: Int, val sailPrice: Int)

    /** Paleta to siatka [COLUMNS] kolumn: jasne, nasycone i ciemne barwy, a pod nimi biele, szarości i brązy. */
    const val COLUMNS = 12

    val palette: List<Shade> = buildList {
        fun row(boatPrice: Int, sailPrice: Int, vararg colors: Int) = colors.forEach { add(Shade(OPAQUE or it, boatPrice, sailPrice)) }
        row(850, 150, 0xF79494, 0xF7B994, 0xF7D394, 0xF7EA94, 0xCEF794, 0x94F7AD, 0x94F7E7, 0x94E4F7, 0x94BEF7, 0x9494F7, 0xCE94F7, 0xF794CE)
        row(750, 100, 0xDB2C2C, 0xDB6C2C, 0xDB9B2C, 0xDBC42C, 0x92DB2C, 0x2CDB58, 0x2CDBBE, 0x2CB8DB, 0x2C75DB, 0x2C2CDB, 0x922CDB, 0xDB2C92)
        row(900, 150, 0x801313, 0x803B13, 0x805813, 0x807113, 0x528013, 0x13802E, 0x13806D, 0x136A80, 0x134080, 0x131380, 0x521380, 0x801352)
        // Biel, srebro, szarość, grafit, czerń, krem, beż, jasny brąz, brąz, czekolada, wino, nocny granat.
        for ((color, boat, sail) in listOf(
            Triple(0xEEEEEE, 1000, 100), Triple(0xBEC2CA, 1100, 200), Triple(0x8C9098, 800, 100), Triple(0x55585F, 900, 150),
            Triple(0x1E1E24, 1200, 200), Triple(0xF2E6C4, 900, 150), Triple(0xD8BC8E, 850, 150), Triple(0xB4824E, 800, 100),
            Triple(0x7E4E2A, 800, 100), Triple(0x4C2C18, 900, 150), Triple(0x7A1E36, 950, 150), Triple(0x1C2850, 1000, 200),
        )) add(Shade(OPAQUE or color, boat, sail))
    }

    /** Odcień i wzór, od których zaczyna kreator. */
    val DEFAULT_BOAT = OPAQUE or 0x2CB8DB
    val DEFAULT_SAIL = SailDesign(SailPattern.DIAGONAL, OPAQUE or 0xEEEEEE, OPAQUE or 0x2C75DB)

    fun patternPrice(pattern: SailPattern): Int = when (pattern) {
        SailPattern.HALVES, SailPattern.VERTICAL -> 300
        SailPattern.BAND -> 350
        SailPattern.STRIPES, SailPattern.DIAGONAL, SailPattern.TRIANGLE -> 400
        SailPattern.CROSS, SailPattern.FRAME -> 450
        SailPattern.DOTS, SailPattern.CHECKER -> 500
    }

    fun shade(color: Int): Shade? = palette.firstOrNull { it.color == (OPAQUE or color) }

    /**
     * Wycena projektu: [base] to suma cen odcienia (i wzoru), [similar] – najdroższa podobna rzecz ze sklepu.
     * Gdy taka jest, [price] przebija jej cenę o co najmniej 25%.
     */
    class Quote(val price: Int, val base: Int, val similar: ShopItem?) {
        val raised: Boolean get() = price > base
    }

    fun boatQuote(color: Int): Quote? {
        val shade = shade(color) ?: return null
        val hull = labs(HullPaints.shades(shade.color))
        val similar = presetHulls.filter { (_, preset) -> hullDistance(hull, preset) < HULL_SIMILAR }.keys.maxByOrNull { it.price }
        return quote(shade.boatPrice, similar)
    }

    /** Wycena żagla; null, gdy kolor spoza palety albo oba kolory są takie same (to nie byłby dwukolorowy żagiel). */
    fun sailQuote(design: SailDesign): Quote? {
        val a = shade(design.first) ?: return null
        val b = shade(design.second) ?: return null
        if (a === b) return null
        // Porównanie z każdym żaglem ze sklepu pole po polu – wynik zapamiętany, bo sklep pyta o niego przy każdym odświeżeniu.
        return sailQuotes.getOrPut(design.id) {
            val grid = labGrid(design.grid())
            val similar = presetSails.filter { (_, preset) -> sailMatch(grid, preset) >= SAIL_MATCH }.keys.maxByOrNull { it.price }
            quote(patternPrice(design.pattern) + a.sailPrice + b.sailPrice, similar)
        }
    }

    private val sailQuotes = ConcurrentHashMap<String, Quote>()

    private fun quote(base: Int, similar: ShopItem?): Quote {
        // Co najmniej o 1/4 drożej niż w sklepie, zaokrąglone w górę do 50 monet.
        val floor = similar?.let { (it.price * 5 + 199) / 200 * 50 } ?: 0
        return Quote(max(base, floor), base, similar)
    }

    fun boatItem(color: Int): ShopItem? = boatQuote(color)?.let { ShopItem(ItemKind.BOAT, HullPaints.ownId(OPAQUE or color), it.price) }

    fun sailItem(design: SailDesign): ShopItem? = sailQuote(design)?.let { ShopItem(ItemKind.SAIL, design.id, it.price) }

    /** Rzecz z kreatora po kluczu z zapisu („boat:own-…”, „sail:own-…”); null dla innych kluczy i kolorów spoza palety. */
    fun find(key: String): ShopItem? = when {
        key.startsWith("boat:") -> HullPaints.ownColor(key.removePrefix("boat:"))?.let(::boatItem)
        key.startsWith("sail:") -> SailDesign.parse(key.removePrefix("sail:"))?.let(::sailItem)
        else -> null
    }

    fun isCreation(item: ShopItem): Boolean = item.id.startsWith("own-")

    // ------------------------------------------------------------ podobieństwo kolorów

    /*
     * Progi celowo hojne – lepiej policzyć drożej niż sprzedać taniej to samo co w sklepie.
     * Różnica kolorów to ΔE w przestrzeni Lab (ok. 2 = ledwo widać, 30 = ten sam kolor w innym odcieniu).
     */

    /** Kadłub jest podobny, gdy średnia różnica jego odcieni jest mniejsza niż ta. */
    const val HULL_SIMILAR = 22f

    /** Pole żagla „ma ten sam kolor”, gdy różni się najwyżej o tyle. */
    const val CELL_SIMILAR = 30f

    /** Żagiel jest podobny, gdy tak duża część pól ma ten sam kolor (wzór i kolory prawie się pokrywają). */
    const val SAIL_MATCH = 0.9f

    private const val OPAQUE = 0xFF000000.toInt()

    private val presetHulls: Map<ShopItem, Array<FloatArray>> by lazy {
        Catalog.boats.associateWith { labs(HullPaints.presets.getValue(it.id)) }
    }

    private val presetSails: Map<ShopItem, Array<Array<FloatArray>>> by lazy {
        Catalog.sails.associateWith { labGrid(SailStyles.grid(it.id)) }
    }

    /** Różnica kadłubów: odcienie ważone liczbą pikseli, które zajmują na łodzi. */
    fun hullDistance(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        val w = HullPaints.WEIGHTS
        return a.indices.sumOf { (w[it] * deltaE(a[it], b[it])).toDouble() }.toFloat() / w.sum()
    }

    /** Jaka część pól żagla ma ten sam kolor; żagiel odwraca się z kursem łodzi, więc liczy się też odbicie. */
    fun sailMatch(a: Array<Array<FloatArray>>, b: Array<Array<FloatArray>>): Float {
        var straight = 0
        var mirrored = 0
        for (row in a.indices) {
            val cols = a[row].size
            for (col in 0 until cols) {
                if (deltaE(a[row][col], b[row][col]) <= CELL_SIMILAR) straight++
                if (deltaE(a[row][col], b[row][cols - 1 - col]) <= CELL_SIMILAR) mirrored++
            }
        }
        return max(straight, mirrored).toFloat() / (a.size * a[0].size)
    }

    fun labs(colors: IntArray): Array<FloatArray> = Array(colors.size) { lab(colors[it]) }

    fun labGrid(grid: Array<IntArray>): Array<Array<FloatArray>> = Array(grid.size) { labs(grid[it]) }

    private fun deltaE(a: FloatArray, b: FloatArray): Float {
        val l = a[0] - b[0]
        val x = a[1] - b[1]
        val y = a[2] - b[2]
        return sqrt(l * l + x * x + y * y)
    }

    /** sRGB → CIE Lab (biel D65). */
    fun lab(color: Int): FloatArray {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(color shr 16 and 0xFF)
        val g = linear(color shr 8 and 0xFF)
        val b = linear(color and 0xFF)
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
        val fy = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
        val fz = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
        return floatArrayOf((116 * fy - 16).toFloat(), (500 * (fx - fy)).toFloat(), (200 * (fy - fz)).toFloat())
    }
}
