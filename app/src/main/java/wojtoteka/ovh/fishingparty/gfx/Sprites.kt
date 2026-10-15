package wojtoteka.ovh.fishingparty.gfx

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import wojtoteka.ovh.fishingparty.R

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

    fun get(path: String): Bitmap = cache.getOrPut(path) { ownBoat(path) ?: load(path) }

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

    fun sprite(name: String): Bitmap = get("sprites/$name")

    fun boat(skin: String, part: String): Bitmap = get("sprites/boats/$skin/$part")

    fun sailor(id: String): Bitmap = get("sprites/sailors/$id")

    fun tiles(theme: String): TileSet = tileSets.getOrPut(theme) { TileSet(theme) }
}

class TileSet(private val theme: String) {
    private fun t(name: String) = Sprites.get("tiles/$theme/$name")

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
