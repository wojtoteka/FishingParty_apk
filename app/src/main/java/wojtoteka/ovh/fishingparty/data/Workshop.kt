package wojtoteka.ovh.fishingparty.data

import wojtoteka.ovh.fishingparty.gfx.ColorMath
import wojtoteka.ovh.fishingparty.gfx.Hsv
import wojtoteka.ovh.fishingparty.gfx.HullPaints
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import wojtoteka.ovh.fishingparty.gfx.SailStyles
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.max

/**
 * Kreator wyglądu w sklepie: dowolny kolor łodzi z pełnej palety i dwukolorowy żagiel.
 * Cena koloru zależy od grupy, do której wpada (barwa × odcień albo biel, szarość, czerń).
 * Projekt podobny do czegoś ze sklepu kosztuje zawsze więcej niż ta rzecz – kreator
 * nie jest tańszą drogą do tego samego wyglądu.
 */
object Workshop {
    /** Rodziny barw – zakres w stopniach koła barw (czerwień przechodzi przez 0°). */
    enum class Hue(val from: Int, val to: Int, val center: Int, val boatPrice: Int) {
        RED(345, 15, 0, 750),
        ORANGE(15, 40, 28, 750),
        YELLOW(40, 70, 55, 800),
        GREEN(70, 160, 115, 700),
        TEAL(160, 200, 180, 800),
        BLUE(200, 250, 222, 700),
        PURPLE(250, 290, 270, 850),
        PINK(290, 345, 318, 800);

        fun contains(degrees: Float): Boolean = if (from < to) degrees >= from && degrees < to else degrees >= from || degrees < to
    }

    /** Odcień barwy: dopłata do ceny rodziny i przykładowe nasycenie/jasność (do cennika). */
    enum class Tone(val extra: Int, val s: Float, val v: Float) {
        PASTEL(100, 0.35f, 0.97f),
        VIVID(0, 0.8f, 0.86f),
        DARK(150, 0.85f, 0.38f),
    }

    /** Kolory bez barwy, od najjaśniejszego. */
    enum class Neutral(val boatPrice: Int, val v: Float) {
        WHITE(1000, 0.95f),
        SILVER(1100, 0.8f),
        GREY(800, 0.57f),
        GRAPHITE(900, 0.3f),
        BLACK(1200, 0.1f),
    }

    /** Grupa kolorów z własną ceną; [sample] to jej typowy kolor (w cenniku). */
    class ColorGroup(val hue: Hue?, val tone: Tone?, val neutral: Neutral?, val boatPrice: Int, val sample: Hsv) {
        /** Jeden kolor żagla kosztuje mniej więcej piątą część koloru łodzi (w górę do 50). */
        val sailPrice: Int get() = (boatPrice / 5 + 49) / 50 * 50
    }

    /** Wszystkie grupy: wiersze pastelowe, nasycone i ciemne po 8 barw, na końcu biele, szarości i czerń. */
    val groups: List<ColorGroup> = buildList {
        for (tone in Tone.entries) for (hue in Hue.entries) {
            add(ColorGroup(hue, tone, null, hue.boatPrice + tone.extra, Hsv(hue.center / 360f, tone.s, tone.v)))
        }
        for (n in Neutral.entries) add(ColorGroup(null, null, n, n.boatPrice, Hsv(0f, 0f, n.v)))
    }

    fun groupOf(color: Int): ColorGroup {
        val c = ColorMath.toHsv(color)
        val neutral = when {
            looksBlack(color, c) -> Neutral.BLACK
            c.s >= 0.15f -> null
            c.v < 0.42f -> Neutral.GRAPHITE
            c.v < 0.72f -> Neutral.GREY
            c.v < 0.9f -> Neutral.SILVER
            else -> Neutral.WHITE
        }
        if (neutral != null) return groups.first { it.neutral == neutral }
        val hue = Hue.entries.first { it.contains(c.h * 360f) }
        val tone = when {
            c.v < 0.55f -> Tone.DARK
            c.s < 0.45f -> Tone.PASTEL
            else -> Tone.VIVID
        }
        return groups.first { it.hue == hue && it.tone == tone }
    }

    /**
     * Czerń to nie tylko najniższa jasność na palecie: bardzo ciemny granat czy bordo też wyglądają
     * na łodzi jak czarne. Liczy się jasność widziana przez oko (L* w Lab) – poniżej 10 każdy kolor
     * jest czarny, a do 20 – gdy ma mało barwy.
     */
    private fun looksBlack(color: Int, hsv: Hsv): Boolean {
        if (hsv.v < 0.18f) return true
        val lab = ColorMath.lab(color)
        return lab[0] < 10f || (lab[0] < 20f && hypot(lab[1], lab[2]) < 25f)
    }

    /** Kolory, od których zaczyna kreator. */
    val DEFAULT_BOAT = Hsv(192 / 360f, 0.8f, 0.86f)
    val DEFAULT_SAIL_PATTERN = SailPattern.DIAGONAL
    val DEFAULT_SAIL_FIRST = Hsv(0f, 0f, 0.93f)
    val DEFAULT_SAIL_SECOND = Hsv(215 / 360f, 0.8f, 0.86f)

    fun patternPrice(pattern: SailPattern): Int = when (pattern) {
        SailPattern.HALVES, SailPattern.VERTICAL -> 300
        SailPattern.BAND -> 350
        SailPattern.STRIPES, SailPattern.DIAGONAL, SailPattern.TRIANGLE -> 400
        SailPattern.CROSS, SailPattern.FRAME -> 450
        SailPattern.DOTS, SailPattern.CHECKER -> 500
    }

    /**
     * Wycena projektu: [base] to cena grupy koloru (i wzoru), [similar] – najdroższa podobna rzecz ze sklepu.
     * Gdy taka jest, [price] przebija jej cenę o co najmniej 25%.
     */
    class Quote(val price: Int, val base: Int, val similar: ShopItem?) {
        val raised: Boolean get() = price > base
    }

    fun boatQuote(color: Int): Quote {
        val hull = labs(HullPaints.shades(color))
        val group = groupOf(color)
        val similar = presetHulls.filter { (boat, preset) -> hullDistance(hull, preset) < HULL_SIMILAR || sameNeutral(group, presetGroups.getValue(boat)) }
            .keys.maxByOrNull { it.price }
        return quote(group.boatPrice, similar)
    }

    /** Biel, szarość czy czerń nie mają barwy – dwa kolory z tej samej takiej grupy wyglądają tak samo. */
    private fun sameNeutral(a: ColorGroup, b: ColorGroup) = a.neutral != null && a === b

    /** Wycena żagla; null, gdy kolory są prawie takie same (to nie byłby dwukolorowy żagiel). */
    fun sailQuote(design: SailDesign): Quote? {
        if (!twoColors(design.first, design.second)) return null
        if (sailQuotes.size > 512) sailQuotes.clear()
        // Porównanie z każdym żaglem ze sklepu pole po polu – wynik zapamiętany, bo sklep pyta o niego przy każdym odświeżeniu.
        return sailQuotes.getOrPut(design.id) {
            val grid = swatchGrid(design.grid())
            val similar = presetSails.filter { (_, preset) -> sailMatch(grid, preset) >= SAIL_MATCH }.keys.maxByOrNull { it.price }
            quote(patternPrice(design.pattern) + groupOf(design.first).sailPrice + groupOf(design.second).sailPrice, similar)
        }
    }

    /** Czy dwa kolory żagla da się odróżnić na łodzi. */
    fun twoColors(a: Int, b: Int): Boolean = ColorMath.deltaE(ColorMath.lab(a), ColorMath.lab(b)) >= MIN_CONTRAST

    private val sailQuotes = ConcurrentHashMap<String, Quote>()

    private fun quote(base: Int, similar: ShopItem?): Quote {
        // Co najmniej o 1/4 drożej niż w sklepie, zaokrąglone w górę do 50 monet.
        val floor = similar?.let { (it.price * 5 + 199) / 200 * 50 } ?: 0
        return Quote(max(base, floor), base, similar)
    }

    fun boatItem(color: Int): ShopItem = ShopItem(ItemKind.BOAT, HullPaints.ownId(color), boatQuote(color).price)

    fun sailItem(design: SailDesign): ShopItem? = sailQuote(design)?.let { ShopItem(ItemKind.SAIL, design.id, it.price) }

    /** Rzecz z kreatora po kluczu z zapisu („boat:own-…”, „sail:own-…”); null dla innych kluczy. */
    fun find(key: String): ShopItem? = when {
        key.startsWith("boat:") -> HullPaints.ownColor(key.removePrefix("boat:"))?.let(::boatItem)
        key.startsWith("sail:") -> SailDesign.parse(key.removePrefix("sail:"))?.let(::sailItem)
        else -> null
    }

    fun isCreation(item: ShopItem): Boolean = item.id.startsWith("own-")

    // ------------------------------------------------------------ podobieństwo kolorów

    /*
     * Progi celowo hojne – lepiej policzyć drożej niż sprzedać taniej to samo co w sklepie.
     * Różnice kolorów to ΔE w przestrzeni Lab (ColorMath.deltaE).
     */

    /** Kadłub jest podobny, gdy średnia różnica jego odcieni jest mniejsza niż ta. */
    const val HULL_SIMILAR = 26f

    /** Pole żagla „ma ten sam kolor”, gdy różni się najwyżej o tyle (albo oba są z tej samej grupy bez barwy). */
    const val CELL_SIMILAR = 30f

    /** Żagiel jest podobny, gdy tak duża część pól ma ten sam kolor (wzór i kolory prawie się pokrywają). */
    const val SAIL_MATCH = 0.9f

    /** Najmniejsza różnica dwóch kolorów żagla. */
    const val MIN_CONTRAST = 10f

    private val presetHulls: Map<ShopItem, Array<FloatArray>> by lazy {
        Catalog.boats.associateWith { labs(HullPaints.presets.getValue(it.id)) }
    }

    /** Grupa koloru głównego (drugiego odcienia) każdej łodzi ze sklepu. */
    private val presetGroups: Map<ShopItem, ColorGroup> by lazy {
        Catalog.boats.associateWith { groupOf(HullPaints.presets.getValue(it.id)[1]) }
    }

    private val presetSails: Map<ShopItem, Array<Array<Swatch>>> by lazy {
        Catalog.sails.associateWith { swatchGrid(SailStyles.grid(it.id)) }
    }

    /** Pole żagla do porównań: kolor w Lab i grupa bez barwy (biel, szarość, czerń…), jeśli do takiej należy. */
    class Swatch(val lab: FloatArray, val neutral: Neutral?)

    fun swatchGrid(grid: Array<IntArray>): Array<Array<Swatch>> {
        val known = HashMap<Int, Swatch>()
        return Array(grid.size) { r ->
            Array(grid[r].size) { c -> known.getOrPut(grid[r][c]) { Swatch(ColorMath.lab(grid[r][c]), groupOf(grid[r][c]).neutral) } }
        }
    }

    private fun sameColor(a: Swatch, b: Swatch): Boolean =
        ColorMath.deltaE(a.lab, b.lab) <= CELL_SIMILAR || (a.neutral != null && a.neutral == b.neutral)

    /** Różnica kadłubów: odcienie ważone liczbą pikseli, które zajmują na łodzi. */
    fun hullDistance(a: Array<FloatArray>, b: Array<FloatArray>): Float {
        val w = HullPaints.WEIGHTS
        return a.indices.sumOf { (w[it] * ColorMath.deltaE(a[it], b[it])).toDouble() }.toFloat() / w.sum()
    }

    /** Jaka część pól żagla ma ten sam kolor; żagiel odwraca się z kursem łodzi, więc liczy się też odbicie. */
    fun sailMatch(a: Array<Array<Swatch>>, b: Array<Array<Swatch>>): Float {
        var straight = 0
        var mirrored = 0
        for (row in a.indices) {
            val cols = a[row].size
            for (col in 0 until cols) {
                if (sameColor(a[row][col], b[row][col])) straight++
                if (sameColor(a[row][col], b[row][cols - 1 - col])) mirrored++
            }
        }
        return max(straight, mirrored).toFloat() / (a.size * a[0].size)
    }

    fun labs(colors: IntArray): Array<FloatArray> = Array(colors.size) { ColorMath.lab(colors[it]) }
}
