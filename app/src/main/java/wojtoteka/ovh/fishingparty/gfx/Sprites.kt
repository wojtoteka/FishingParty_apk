package wojtoteka.ovh.fishingparty.gfx

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.level.Worlds

/**
 * Wszystkie grafiki gry to oryginalne sprity (i ich przemalowane wersje)
 * z katalogu assets. Nic nie jest rysowane proceduralnie – jedynie łódź z kreatora
 * kolorów to oryginalny kadłub przemalowany w grze, tak samo jak robi to skrypt.
 */
object Sprites {
    private lateinit var assets: AssetManager
    private val cache = HashMap<String, Bitmap>()
    private val tileSets = HashMap<String, TileSet>()
    lateinit var font: Typeface
        private set

    fun init(context: Context) {
        assets = context.applicationContext.assets
        font = ResourcesCompat.getFont(context, R.font.bitcell) ?: Typeface.MONOSPACE
    }

    private fun load(path: String): Bitmap {
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return assets.open("$path.png").use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Brak grafiki $path")
    }

    /** Łodzie z kreatora – przy przeciąganiu palcem po palecie powstaje ich dużo, więc pamiętamy tylko ostatnie. */
    private val ownBoats = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > 48
    }

    fun get(path: String): Bitmap = cache[path] ?: ownBoats[path] ?: ownBoat(path)?.also { ownBoats[path] = it }
        ?: sandTile(path)?.also { cache[path] = it } ?: load(path).also { cache[path] = it }

    /** Łódź z kreatora („sprites/boats/own-RRGGBB/część”): klasyczny kadłub w odcieniach wybranego koloru. */
    private fun ownBoat(path: String): Bitmap? {
        val parts = path.split('/')
        if (parts.size != 4 || parts[0] != "sprites" || parts[1] != "boats") return null
        val shades = HullPaints.ownColor(parts[2])?.let(HullPaints::shades) ?: return null
        val source = get("sprites/boats/classic/${parts[3]}")
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val k = HullPaints.ORIGINAL.indexOf(pixels[i])
            if (k >= 0) pixels[i] = shades[k]
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Kafelek palety z piaskiem w kolorze innej palety („tiles/paleta+piasek/nazwa”, kreator poziomów): dwa odcienie
     * piasku przemalowane, woda i piana przy brzegu zostają. Kafelki piasku wszystkich palet mają ten sam rysunek,
     * więc odcienie paruje się po miejscu w „sandfull”.
     */
    private fun sandTile(path: String): Bitmap? {
        val parts = path.split('/')
        if (parts.size != 3 || parts[0] != "tiles") return null
        val themes = parts[1].split('+')
        if (themes.size != 2) return null
        val source = get("tiles/${themes[0]}/${parts[2]}")
        if (!parts[2].startsWith("sand") && parts[2] != "wallsand") return source
        val from = get("tiles/${themes[0]}/sandfull")
        val to = get("tiles/${themes[1]}/sandfull")
        val shades = HashMap<Int, Int>()
        for (y in 0 until from.height) for (x in 0 until from.width) shades[from.getPixel(x, y)] = to.getPixel(x, y)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            // Pojedyncze piksele brzegu bywają o włos inne niż odcień z „sandfull” – też są piaskiem.
            val shade = shades[p] ?: shades.entries.firstOrNull { (k, _) -> close(k, p) }?.value ?: continue
            pixels[i] = shade
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }

    private fun close(a: Int, b: Int): Boolean =
        (0..24 step 8).all { shift -> kotlin.math.abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 2 }

    fun sprite(name: String): Bitmap = get("sprites/$name")

    fun boat(skin: String, part: String): Bitmap = get("sprites/boats/$skin/$part")

    fun sailor(id: String): Bitmap = get("sprites/sailors/$id")

    fun tiles(theme: String): TileSet = tileSets.getOrPut(theme) { TileSet(theme) }
}

class TileSet(private val theme: String) {
    private fun t(name: String) = Sprites.get("tiles/$theme/$name")

    /** Te kafelki z piaskiem w kolorze palety świata [world] (kreator poziomów). */
    fun withSand(world: Int): TileSet {
        val sand = Worlds.theme(world)
        return if (sand == theme) this else Sprites.tiles("$theme+$sand")
    }

    val sea = t("sea")
    val waves = Array(5) { t("wave$it") }
    val sandFull = t("sandfull")
    val sandTop = Array(2) { t("sandtop$it") }
    val sandBottom = Array(2) { t("sandbottom$it") }
    val sandLeft = Array(2) { t("sandleft$it") }
    val sandRight = Array(2) { t("sandright$it") }
    val sandTopLeft = Array(2) { t("sandtopleft$it") }
    val sandTopRight = Array(2) { t("sandtopright$it") }
    val sandLeftBottom = Array(2) { t("sandleftbottom$it") }
    val sandBottomRight = Array(2) { t("sandbottomright$it") }
    val rocks = Array(3) { t("rock$it") }
    val walls = Array(2) { t("wall$it") }
    val wallSand = t("wallsand")
    val wallTile = t("walltile")
    val doors = Array(2) { t("door$it") }
    val crab = t("crab")
}
