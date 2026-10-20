package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import wojtoteka.ovh.fishingparty.FishingPartyApp
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.audio.AudioEngine
import wojtoteka.ovh.fishingparty.data.GameRepository
import wojtoteka.ovh.fishingparty.data.LevelDrafts
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.online.OnlineManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Paleta wzięta z oryginalnych spritów. */
object Pal {
    val Outline = Color(0xFF0A1E38)
    val Navy = Color(0xFF193771)
    val LevelBlue = Color(0xFF195471)
    val Sea = Color(0xFF0097E3)
    val SeaLight = Color(0xFF7FCBF1)
    val SeaDark = Color(0xFF004C72)
    val Foam = Color(0xFFE3ECFF)
    val Sand = Color(0xFFFFB31C)
    val SandLight = Color(0xFFFFD071)
    val SandDark = Color(0xFFE38814)
    val Wood = Color(0xFFAA5226)
    val WoodLight = Color(0xFFE0885C)
    val WoodDark = Color(0xFF7C2800)
    val Green = Color(0xFF45D183)
    val GreenLight = Color(0xFFC4FF82)
    val GreenDark = Color(0xFF21AE63)
    val Red = Color(0xFFFF3939)
    val RedLight = Color(0xFFFF8C82)
    val RedDark = Color(0xFFC62C2C)
    val Stone = Color(0xFFAA9184)
    val StoneLight = Color(0xFFE3C1B0)
    val StoneDark = Color(0xFF856C5F)
    val Grey = Color(0xFF878787)
    val GreyLight = Color(0xFFB4B4B4)
    val GreyDark = Color(0xFF5A5A5A)
    val Gold = Color(0xFFFFC420)
    val Orange = Color(0xFFFF8A1E)
    val OrangeLight = Color(0xFFFFC27A)
    val OrangeDark = Color(0xFFD25E00)
    val Purple = Color(0xFF9A52E0)
    val PurpleLight = Color(0xFFD7B0FF)
    val PurpleDark = Color(0xFF6A2DB0)
    val Teal = Color(0xFF00AAAA)
    val TealLight = Color(0xFF7FE6DC)
    val TealDark = Color(0xFF007A7A)
    val White = Color.White
    val Scrim = Color(0xA0000000)
}

class Services(val repo: GameRepository, val audio: AudioEngine, val online: OnlineManager, val drafts: LevelDrafts)

val LocalServices = staticCompositionLocalOf<Services> { error("Brak usług") }

/** Rozmiar „piksela interfejsu” w pikselach ekranu (3 na typowym telefonie 1080 px). */
val LocalUnit = staticCompositionLocalOf { 3 }

val BitCell = FontFamily(Font(R.font.bitcell))

/**
 * Wspólny miernik tekstu z dużą pamięcią. Każdy [PixelText] miał własny, więc w listach (sklep, odznaki)
 * każda nowa karta przy przewijaniu od nowa układała te same napisy – ceny, „WYBIERZ”, „W UŻYCIU”.
 */
val LocalTextMeasurer = staticCompositionLocalOf<TextMeasurer?> { null }

@Composable
fun ProvideUi(app: FishingPartyApp, content: @Composable () -> Unit) {
    // Niski ekran (tablet w poziomie) bierze skalę z wysokości, żeby menu się mieściło.
    val window = LocalWindowInfo.current.containerSize
    val unit = max(2, min(window.width / 360f, window.height / 720f).roundToInt())
    val services = remember(app) { Services(app.repository, app.audio, app.online, app.drafts) }
    val measurer = rememberTextMeasurer(cacheSize = 512)
    CompositionLocalProvider(LocalServices provides services, LocalUnit provides unit, LocalTextMeasurer provides measurer, content = content)
}

/** Piksele interfejsu → dp. */
@Composable
fun px(units: Int): Dp {
    val u = LocalUnit.current
    return with(LocalDensity.current) { (units * u).toDp() }
}

@Composable
fun px(units: Float): Dp {
    val u = LocalUnit.current
    return with(LocalDensity.current) { (units * u).toDp() }
}

/** Rozmiar fontu: 1 = mały, 2 = zwykły, 3 = duży, 4+ = nagłówki. Zawsze całkowite piksele – litery są ostre. */
@Composable
fun fontPixels(size: Int): Int = fontPixels(size, LocalUnit.current)

fun fontPixels(size: Int, u: Int): Int = when (size) {
    1 -> u
    2 -> (u * 4f / 3f).roundToInt()
    else -> u * (size - 1)
}

/** Styl napisu [PixelText] (fp – piksele fontu z [fontPixels]); ten sam w kartach rysowanych ręcznie. */
fun pixelTextStyle(fp: Int, color: Color, align: TextAlign, density: Density): TextStyle = with(density) {
    TextStyle(
        fontFamily = BitCell,
        fontSize = (16 * fp).toSp(),
        lineHeight = (11 * fp).toSp(),
        color = color,
        textAlign = align,
    )
}

/**
 * Mierzy napis jak [PixelText]. [maxWidth] obejmuje obrys (po pikselu fontu z każdej strony),
 * a pełny rozmiar z obrysem to rozmiar wyniku powiększony o 2·fp.
 */
fun TextMeasurer.measurePixelText(
    text: String,
    fp: Int,
    color: Color,
    density: Density,
    maxWidth: Int,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    outline: Boolean = true,
): TextLayoutResult {
    val o = if (outline) fp else 0
    return measure(
        AnnotatedString(text),
        pixelTextStyle(fp, color, align, density),
        constraints = Constraints(maxWidth = max(0, maxWidth - 2 * o)),
        maxLines = maxLines,
    )
}

/** Rysuje zmierzony napis z obrysem tak jak [PixelText]; (x, y) to lewy górny róg razem z obrysem. */
fun DrawScope.drawPixelText(r: TextLayoutResult, x: Float, y: Float, fp: Int, outline: Color? = Pal.Outline) {
    val o = if (outline != null) fp.toFloat() else 0f
    if (outline != null) {
        for ((dx, dy) in OUTLINE_OFFSETS) {
            drawText(r, color = outline, topLeft = Offset(x + o + dx * fp, y + o + dy * fp))
        }
    }
    drawText(r, topLeft = Offset(x + o, y + o))
}

/**
 * Wynik pomiaru tekstu. Stan Compose, a nie zwykłe pola: gdy tekst się zmienia, a szerokość
 * zostaje ta sama (np. sekundy w odliczaniu), węzeł nie zmienia rozmiaru i bez odczytu stanu
 * w rysowaniu na ekranie zostawałby stary napis.
 */
private class TextHolder {
    var result by mutableStateOf<TextLayoutResult?>(null)
    var offsetX by mutableIntStateOf(0)
}

/** Tekst BitCell z pikselowym obrysem (jak napisy w grze). */
@Composable
fun PixelText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 2,
    color: Color = Pal.White,
    outline: Color? = Pal.Outline,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    val fp = fontPixels(size)
    val density = LocalDensity.current
    val measurer = LocalTextMeasurer.current ?: rememberTextMeasurer(cacheSize = 4)
    val style = remember(fp, color, align, density) { pixelTextStyle(fp, color, align, density) }
    val holder = remember { TextHolder() }
    Layout(
        modifier = modifier.drawBehind {
            val r = holder.result ?: return@drawBehind
            drawPixelText(r, holder.offsetX.toFloat(), 0f, fp, outline)
        },
    ) { _, constraints ->
        val o = if (outline != null) fp else 0
        val maxW = if (constraints.hasBoundedWidth) max(0, constraints.maxWidth - 2 * o) else Constraints.Infinity
        val r = measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = maxW), maxLines = maxLines)
        holder.result = r
        val contentW = r.size.width + 2 * o
        val w = max(constraints.minWidth, contentW).coerceAtMost(if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
        holder.offsetX = when (align) {
            TextAlign.Center -> (w - contentW) / 2
            TextAlign.End, TextAlign.Right -> w - contentW
            else -> 0
        }
        layout(w, max(constraints.minHeight, r.size.height + 2 * o)) {}
    }
}

private val OUTLINE_OFFSETS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, 1 to 1)

@Composable
fun rememberSprite(path: String): ImageBitmap = remember(path) { Sprites.get(path).asImageBitmap() }

@Composable
fun rememberSprite(bitmap: Bitmap): ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

/** Sprite w skali całkowitej (piksele zostają kwadratowe). */
@Composable
fun SpriteImage(path: String, scale: Int, modifier: Modifier = Modifier, alpha: Float = 1f, flipX: Boolean = false) {
    val image = rememberSprite("sprites/$path")
    SpriteImage(image, scale, modifier, alpha, flipX)
}

@Composable
fun SpriteImage(image: ImageBitmap, scale: Int, modifier: Modifier = Modifier, alpha: Float = 1f, flipX: Boolean = false) {
    val u = LocalUnit.current
    val k = scale * u
    val density = LocalDensity.current
    val w = with(density) { (image.width * k).toDp() }
    val h = with(density) { (image.height * k).toDp() }
    Canvas(modifier.size(w, h)) {
        drawSprite(image, Offset.Zero, k.toFloat(), alpha, flipX)
    }
}

fun DrawScope.drawSprite(image: ImageBitmap, topLeft: Offset, k: Float, alpha: Float = 1f, flipX: Boolean = false) {
    val dstW = (image.width * k).roundToInt()
    val dstH = (image.height * k).roundToInt()
    if (flipX) {
        drawContext.transform.scale(-1f, 1f, Offset(topLeft.x + dstW / 2f, topLeft.y + dstH / 2f))
    }
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(dstW, dstH),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
    if (flipX) {
        drawContext.transform.scale(-1f, 1f, Offset(topLeft.x + dstW / 2f, topLeft.y + dstH / 2f))
    }
}

/** Kafelek powtarzany jako tekstura tła. */
fun DrawScope.drawTiled(image: ImageBitmap, k: Float, alpha: Float = 1f) {
    val step = image.width * k
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            drawSprite(image, Offset(x, y), k, alpha)
            x += step
        }
        y += step
    }
}

enum class ButtonStyle(val base: Color, val light: Color, val dark: Color, val text: Color = Pal.White) {
    GREEN(Pal.Green, Pal.GreenLight, Pal.GreenDark),
    SAND(Pal.Sand, Pal.SandLight, Pal.SandDark),
    SEA(Pal.Sea, Pal.SeaLight, Pal.SeaDark),
    WOOD(Pal.Wood, Pal.WoodLight, Pal.WoodDark),
    RED(Pal.Red, Pal.RedLight, Pal.RedDark),
    GREY(Pal.Grey, Pal.GreyLight, Pal.GreyDark),
    /** Poziom dnia. */
    ORANGE(Pal.Orange, Pal.OrangeLight, Pal.OrangeDark),
    /** Poziom tygodnia. */
    PURPLE(Pal.Purple, Pal.PurpleLight, Pal.PurpleDark),

    /** Poziomy graczy. */
    TEAL(Pal.Teal, Pal.TealLight, Pal.TealDark),
}

/** Prostokąt w stylu pikselowym: obrys z „uciętymi” narożnikami, jasna krawędź u góry, cień u dołu. */
fun DrawScope.pixelBox(
    u: Float,
    base: Color,
    light: Color,
    dark: Color,
    outline: Color = Pal.Outline,
    shadow: Float = 2f,
    w: Float = size.width,
    h: Float = size.height,
) {
    drawRect(outline, Offset(u, 0f), Size(w - 2 * u, h))
    drawRect(outline, Offset(0f, u), Size(w, h - 2 * u))
    drawRect(base, Offset(u, u), Size(w - 2 * u, h - 2 * u))
    drawRect(light, Offset(u, u), Size(w - 2 * u, u))
    if (shadow > 0f) drawRect(dark, Offset(u, h - u - shadow * u), Size(w - 2 * u, shadow * u))
}

/**
 * Zapas od dolnej krawędzi ekranu (w pikselach interfejsu) dla przewijanych list.
 * Zaokrąglone rogi wyświetlacza ścinały narożniki ostatniej karty, gdy stała tuż przy dole.
 */
const val BOTTOM_GAP = 24

/**
 * Złota ramka wokół elementu wybranego z klawiatury (strzałki, Tab). Rysowana na zewnątrz elementu,
 * z odstępem i uciętymi narożnikami jak [pixelBox].
 */
fun DrawScope.focusRing(u: Float, width: Float = size.width, height: Float = size.height) {
    fun ring(gap: Float, line: Float, color: Color) {
        val w = width + 2 * gap
        val h = height + 2 * gap
        drawRect(color, Offset(-gap + u, -gap), Size(w - 2 * u, line))
        drawRect(color, Offset(-gap + u, height + gap - line), Size(w - 2 * u, line))
        drawRect(color, Offset(-gap, -gap + u), Size(line, h - 2 * u))
        drawRect(color, Offset(width + gap - line, -gap + u), Size(line, h - 2 * u))
    }
    // Złoto z ciemnym obrysem po obu stronach – widać je na piasku, wodzie i złotych przyciskach.
    ring(4 * u, 4 * u, Pal.Outline)
    ring(3 * u, 2 * u, Pal.Gold)
}

/** Czy element ma fokus z klawiatury – po dotknięciu ekranu ramka znika, nawet jeśli fokus został. */
@Composable
fun keyFocused(interaction: MutableInteractionSource): Boolean {
    val focused by interaction.collectIsFocusedAsState()
    return focused && LocalInputModeManager.current.inputMode == InputMode.Keyboard
}

/** Najmniejsza wysokość i szerokość przycisku w pikselach interfejsu (ok. 34 dp). */
const val MIN_BUTTON = 32

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.SAND,
    enabled: Boolean = true,
    size: Int = 3,
    icon: String? = null,
    sound: Boolean = true,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val services = LocalServices.current
    val u = LocalUnit.current.toFloat()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused = keyFocused(interaction)
    val s = if (enabled) style else ButtonStyle.GREY
    Box(
        modifier = modifier
            // Wybrany przycisk rysuje się nad sąsiadami, żeby nie zasłaniały mu ramki.
            .zIndex(if (focused) 1f else 0f)
            .drawBehind {
                if (focused) focusRing(u)
                pixelBox(u, s.base, s.light, s.dark, shadow = if (pressed) 1f else 3f)
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                if (sound) services.audio.play(Sfx.CLICK)
                onClick()
            }
            // Minimalny rozmiar – nawet przycisk z małym napisem jest wygodny do trafienia palcem.
            .heightIn(min = px(MIN_BUTTON))
            .widthIn(min = px(MIN_BUTTON))
            .padding(horizontal = px(7), vertical = px(5))
            .offset { IntOffset(0, if (pressed) u.toInt() else -u.toInt()) },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                SpriteImage(icon, 1)
                if (text.isNotEmpty()) Box(Modifier.size(px(3)))
            }
            if (content != null) content() else if (text.isNotEmpty()) {
                PixelText(text, size = size, color = s.text, align = TextAlign.Center)
            }
        }
    }
}

/** Panel jak tabliczka z piasku – tło to oryginalny kafelek piasku. */
@Composable
fun PixelPanel(modifier: Modifier = Modifier, scrollable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val u = LocalUnit.current.toFloat()
    val sand = rememberSprite("tiles/sunny_bay/sandfull")
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .drawBehind {
                pixelBox(u, Pal.WoodDark, Pal.WoodLight, Pal.WoodDark, shadow = 0f)
                val inset = 3 * u
                drawContext.canvas.save()
                drawContext.canvas.clipRect(inset, inset, size.width - inset, size.height - inset)
                drawContext.transform.translate(inset, inset)
                drawTiled(sand, u)
                drawContext.transform.translate(-inset, -inset)
                drawContext.canvas.restore()
            }
            // Ramka stoi w miejscu, przewija się tylko zawartość.
            .then(if (scrollable) Modifier.keyScroll(scroll).verticalScroll(scroll) else Modifier)
            .padding(px(8)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/**
 * Ciemna wnęka pod złote ikony na piaskowym panelu – złota rybka nie ma
 * obrysu i bez tła zlewa się z piaskiem.
 */
@Composable
fun Plate(modifier: Modifier = Modifier, pad: Int = 3, content: @Composable RowScope.() -> Unit) {
    val u = LocalUnit.current.toFloat()
    Row(
        modifier
            .drawBehind { pixelBox(u, Pal.SeaDark, Pal.Navy, Pal.Outline, shadow = 0f) }
            .padding(horizontal = px(pad + 1), vertical = px(pad)),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Ciemna tabliczka (do list i kart). */
@Composable
fun DarkCard(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val u = LocalUnit.current.toFloat()
    Box(
        modifier = modifier
            .drawBehind {
                pixelBox(
                    u,
                    if (highlight) Pal.Navy else Pal.SeaDark.copy(alpha = 0.92f),
                    if (highlight) Pal.Gold else Pal.Sea,
                    Pal.Outline,
                    shadow = 1f,
                )
            }
            .padding(px(6)),
        content = content,
    )
}

/** Monety (złota ryba) – liczba z ikoną. */
@Composable
fun CoinCounter(amount: Long, modifier: Modifier = Modifier, size: Int = 2) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SpriteImage("fishscore_gold", if (size >= 3) 2 else 1)
        Box(Modifier.size(px(2)))
        PixelText(amount.toString(), size = size)
    }
}

@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier, scale: Int = 1, gap: Int = 1) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(px(gap))) {
        for (i in 0 until 3) SpriteImage(if (i < stars) "fishscore_gold" else "fishscore_grey", scale)
    }
}

/** Pasek postępu w stylu pikselowym. */
@Composable
fun PixelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Pal.Gold) {
    val u = LocalUnit.current.toFloat()
    Box(
        modifier.drawBehind {
            drawRect(Pal.Outline)
            drawRect(Pal.SeaDark, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
            val w = (size.width - 2 * u) * fraction.coerceIn(0f, 1f)
            if (w > 0f) {
                drawRect(color, Offset(u, u), Size(w, size.height - 2 * u))
                drawRect(Color.White.copy(alpha = 0.35f), Offset(u, u), Size(w, u))
            }
        },
    )
}

@Composable
fun Modifier.noRippleClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused = keyFocused(interaction)
    val u = LocalUnit.current.toFloat()
    return this
        .zIndex(if (focused) 1f else 0f)
        .drawBehind { if (focused) focusRing(u) }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * Listy (odznaki, ranking) przygotowują karty z wyprzedzeniem – mniej więcej ekran do przodu
 * i pół ekranu wstecz – w wolnym czasie między klatkami. Przy szybkim przewijaniu karta jest już
 * gotowa, zamiast powstawać w tej samej klatce, w której ma się pokazać.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberListState(): LazyListState = rememberLazyListState(LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f))

@Composable
fun VSpace(units: Int) = Box(Modifier.size(width = 1.dp, height = px(units)))

@Composable
fun HSpace(units: Int) = Box(Modifier.size(width = px(units), height = 1.dp))
