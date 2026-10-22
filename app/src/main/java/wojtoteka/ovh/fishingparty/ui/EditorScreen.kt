package wojtoteka.ovh.fishingparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.LevelDraft
import wojtoteka.ovh.fishingparty.data.LevelDrafts
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.Piece
import wojtoteka.ovh.fishingparty.level.PieceRef
import wojtoteka.ovh.fishingparty.level.Placement
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevelEdits
import wojtoteka.ovh.fishingparty.level.UserLevelProblem
import wojtoteka.ovh.fishingparty.level.UserLevelRating
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.level.Worlds
import wojtoteka.ovh.fishingparty.online.COMMUNITY_MIN_PLAYERS
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineResult
import kotlin.math.floor
import kotlin.math.hypot

/** Narzędzia kreatora: przesuwanie, malowanie planszy i stawianie postaci. */
enum class EditorTool(val enemy: EnemyKind? = null) {
    MOVE, SAND, ROCK, ERASE, FISH, BLUE, GOLDEN,
    PIRANHA(EnemyKind.PIRANHA), PIRANHA_VERTICAL(EnemyKind.PIRANHA_VERTICAL), RED_PIRANHA(EnemyKind.RED_PIRANHA),
    OCTOPUS(EnemyKind.OCTOPUS), RED_OCTOPUS(EnemyKind.RED_OCTOPUS), SHARK(EnemyKind.SHARK), DARK_SHARK(EnemyKind.DARK_SHARK),
    STALKER(EnemyKind.STALKER), CRAB, DOOR;

    val paints: Boolean get() = this == SAND || this == ROCK || this == ERASE

    val placement: Placement?
        get() = when {
            this == FISH -> Placement.Fish(false)
            this == BLUE -> Placement.Fish(true)
            this == GOLDEN -> Placement.Golden
            this == CRAB -> Placement.Crab
            enemy != null -> Placement.Enemy(enemy)
            else -> null
        }
}

/** Jak kreator uruchamia grę: test (od wybranego etapu) albo przejście przed publikacją. */
sealed interface EditorRun {
    data class Test(val fromStage: Int) : EditorRun
    data object Verify : EditorRun
}

private enum class EditorDialog { LEVEL, STAGE, PIECE, TEST, PUBLISH, DELETE_STAGE }

/** Stan kreatora: szkic z cofaniem i ponawianiem, narzędzie, przewinięcie i zaznaczenie. */
private class EditorModel(initial: LevelDraft, private val drafts: LevelDrafts) {
    var draft by mutableStateOf(initial)
        private set
    var built by mutableStateOf(UserLevels.build(initial.level))
        private set
    var problems by mutableStateOf(UserLevels.check(initial.level))
        private set
    private val undo = ArrayList<UserLevel>()
    private val redo = ArrayList<UserLevel>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var tool by mutableStateOf(EditorTool.SAND)
    var scroll by mutableFloatStateOf(-1f)
    var active by mutableIntStateOf(0)
    var selected by mutableStateOf<PieceRef?>(null)
    var brush by mutableStateOf<List<Pair<Int, Int>>>(emptyList())
    var dragRef by mutableStateOf<PieceRef?>(null)
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)
    var notice by mutableStateOf<Int?>(null)

    /** Ostatnie rysowanie: skala, przesunięcie planszy i wysokość widoku (piksele ekranu). */
    var scale = 6f
    var offsetX = 0f
    var viewHeight = 1f
    private var stroke = false
    private var strokeStart: UserLevel? = null

    /**
     * Poziom przed poprawkami: pamięta piasek na polach zawsze z wodą (przy wrotach i na starcie), więc
     * przesunięcie etapu tam i z powrotem niczego nie gubi. Historia cofania trzyma takie poziomy.
     */
    private var raw: UserLevel = initial.level

    val level: UserLevel get() = draft.level

    /** Zmiana zrobiona na poziomie po poprawkach ([level]) – malowanie, postacie, ustawienia. */
    fun set(next: UserLevel?): Boolean = apply(next?.let { UserLevelEdits.keepLocked(it, level, raw) })

    /** Jak [set], ale na bieżącym poziomie – szybkie stuknięcia nie liczą od starej wersji. */
    fun edit(change: (UserLevel) -> UserLevel?): Boolean = set(change(level))

    /**
     * Zmiana etapów (kolejność, liczba, rzędy, odbicie) – na poziomie przed poprawkami. Po zmianie
     * kreator wybiera etap [focus] i przewija do niego planszę.
     */
    fun reshape(focus: Int? = null, change: (UserLevel) -> UserLevel?): Boolean {
        if (!apply(change(raw))) return false
        if (focus != null) focusStage(focus)
        return true
    }

    private fun apply(next: UserLevel?): Boolean {
        if (next == null) return false
        val normal = UserLevels.normalize(next)
        if (UserLevels.encode(normal) == UserLevels.encode(level)) return false
        if (!stroke) push(raw)
        raw = next
        draft = draft.with(level = normal, updatedAt = System.currentTimeMillis())
        rebuild()
        if (!stroke) drafts.save(draft)
        return true
    }

    private fun push(previous: UserLevel) {
        undo += previous
        if (undo.size > HISTORY) undo.removeAt(0)
        redo.clear()
        canUndo = true
        canRedo = false
    }

    private fun rebuild() {
        built = UserLevels.build(level)
        problems = UserLevels.check(level)
        active = active.coerceIn(0, level.stages.lastIndex)
        selected = selected?.takeIf { it.stage < level.stages.size }
        clampScroll()
    }

    fun beginStroke() {
        stroke = true
        strokeStart = raw
    }

    fun endStroke() {
        stroke = false
        val start = strokeStart ?: return
        strokeStart = null
        if (UserLevels.encode(start) != UserLevels.encode(raw)) {
            push(start)
            drafts.save(draft)
        }
        brush = emptyList()
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo += raw
        replace(previous)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo += raw
        replace(next)
    }

    private fun replace(next: UserLevel) {
        val stages = level.stages.size
        raw = next
        draft = draft.with(level = UserLevels.normalize(next), updatedAt = System.currentTimeMillis())
        selected = null
        rebuild()
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
        drafts.save(draft)
        // Cofnięta zmiana etapów przesuwa planszę – pokazujemy wybrany etap.
        if (level.stages.size != stages) focusStage(active)
    }

    /** Powrót z gry: szkic mógł zostać zweryfikowany – bierzemy zapisaną wersję (historia cofania zostaje). */
    fun reload(stored: LevelDraft) {
        if (stored === draft) return
        if (UserLevels.encode(stored.level) != UserLevels.encode(level)) raw = stored.level
        draft = stored
        rebuild()
    }

    fun updateDraft(next: LevelDraft) {
        draft = next
        drafts.save(next)
    }

    // ------------------------------------------------------------------ ekran ↔ plansza

    val worldHeight: Float get() = built.layout.grid.height * TILE.toFloat()

    fun clampScroll() {
        val max = (worldHeight - viewHeight / scale).coerceAtLeast(0f)
        scroll = if (scroll < 0f) max else scroll.coerceIn(0f, max)
    }

    fun scrollBy(dyPixels: Float) {
        scroll -= dyPixels / scale
        clampScroll()
        updateActiveFromView()
    }

    private fun updateActiveFromView() {
        val center = ((scroll + viewHeight / scale / 2f) / TILE).toInt()
        stageAt(center)?.let { active = it }
    }

    /** Wybiera etap [k] i przewija planszę tak, żeby był widoczny (wysoki – od dołu, skąd wpływa łódź). */
    fun focusStage(k: Int) {
        active = k.coerceIn(0, level.stages.lastIndex)
        if (scroll < 0f) return
        val s = built.layout.stages[active]
        val view = viewHeight / scale
        val top = s.gateRow * TILE.toFloat()
        val bottom = (s.bottomRow + 2) * TILE.toFloat()
        scroll = if (bottom - top <= view) (top + bottom - view) / 2f else bottom - view
        clampScroll()
    }

    fun worldX(px: Float) = (px - offsetX) / scale

    fun worldY(py: Float) = py / scale + scroll

    /** Etap, w którym leży bezwzględny wiersz siatki (bez bram). */
    fun stageAt(row: Int): Int? = built.layout.stages.indexOfFirst { row in it.topRow..it.bottomRow }.takeIf { it >= 0 }

    fun top(k: Int) = built.layout.stages[k].topRow

    /** Pole pod palcem: etap, kolumna, wiersz etapu. */
    fun cellAt(px: Float, py: Float): Triple<Int, Int, Int>? {
        val col = floor(worldX(px) / TILE).toInt()
        val row = floor(worldY(py) / TILE).toInt()
        if (col !in 0 until WIDTH) return null
        val k = stageAt(row) ?: return null
        return Triple(k, col, row - top(k))
    }

    /** Pola pędzla: piasek 2×2 (blok najbliżej palca, w obrębie etapu), reszta – jedno pole. */
    fun brushAt(px: Float, py: Float): Pair<Int, List<Pair<Int, Int>>>? {
        val (k, col, row) = cellAt(px, py) ?: return null
        if (tool != EditorTool.SAND) return k to listOf(col to row)
        val rows = level.stages[k].rows
        val c0 = floor((worldX(px) - TILE / 2f) / TILE).toInt().coerceIn(0, WIDTH - 2)
        val r0 = (floor((worldY(py) - TILE / 2f) / TILE).toInt() - top(k)).coerceIn(0, rows - 2)
        return k to listOf(c0 to r0, c0 + 1 to r0, c0 to r0 + 1, c0 + 1 to r0 + 1)
    }

    fun paint(px: Float, py: Float) {
        val (k, cells) = brushAt(px, py) ?: return
        active = k
        brush = cells.map { (c, r) -> top(k) + r to c }
        val code = when (tool) {
            EditorTool.SAND -> UserLevels.SAND
            EditorTool.ROCK -> UserLevels.ROCK
            else -> UserLevels.SEA
        }
        set(UserLevelEdits.paint(level, k, cells, code))
    }

    /** Stawia postać narzędziem (albo usuwa taką samą); false – limit postaci albo złe pole. */
    fun place(px: Float, py: Float): Boolean {
        val (k, col, row) = cellAt(px, py) ?: return false
        active = k
        val what = tool.placement ?: return false
        val next = UserLevelEdits.place(level, k, col, row, what)
        if (next == null) {
            val stage = level.stages[k]
            notice = if (!UserLevelEdits.hasRoom(stage, what)) R.string.editor_limit else null
            return false
        }
        set(next)
        return true
    }

    fun moveDoor(px: Float, py: Float) {
        val col = floor(worldX(px) / TILE).toInt()
        val row = floor(worldY(py) / TILE).toInt()
        val stages = built.layout.stages
        // Mur bramy albo pierwszy rząd pod nim.
        val k = stages.indexOfFirst { row == it.gateRow || row == it.gateRow + 1 || row == it.gateRow - 1 }.takeIf { it >= 0 } ?: return
        active = k
        set(UserLevelEdits.setDoor(level, k, col))
    }

    fun pieceAt(px: Float, py: Float): PieceRef? {
        val (k, col, row) = cellAt(px, py) ?: return null
        return UserLevelEdits.pieceAt(level, k, col, row)
    }

    fun drop(ref: PieceRef, px: Float, py: Float): Boolean {
        val (k, col, row) = cellAt(px, py) ?: return false
        val next = UserLevelEdits.move(level, ref, k, col, row) ?: return false
        active = k
        set(next)
        return true
    }

    companion object {
        private const val HISTORY = 60
    }
}

@Composable
fun EditorScreen(draftId: String, onBack: () -> Unit, onPlay: (LevelDraft, EditorRun) -> Unit, onAccount: () -> Unit) {
    val services = LocalServices.current
    val drafts = services.drafts
    val initial = remember(draftId) { drafts.get(draftId) }
    if (initial == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    // Kreator pamięta historię cofania także po teście poziomu (ekran znika na czas gry).
    val model = remember(draftId) {
        sessions.getOrPut(draftId) { EditorModel(initial, drafts) }.also {
            it.reload(initial)
            while (sessions.size > MAX_SESSIONS) sessions.remove(sessions.keys.first { key -> key != draftId })
        }
    }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    BackHandler(enabled = dialog == null) { onBack() }

    Box(Modifier.fillMaxSize().background(Pal.SeaDark)) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(model, onBack = onBack, onDialog = { dialog = it })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EditorCanvas(model, onPiece = {
                    model.selected = it
                    dialog = EditorDialog.PIECE
                })
                model.notice?.let { notice ->
                    LaunchedEffect(notice) {
                        delay(2200)
                        model.notice = null
                    }
                    Box(Modifier.align(Alignment.TopCenter).padding(px(6))) {
                        DarkCard(highlight = true) { PixelText(stringResource(notice), size = 1, color = Pal.Gold) }
                    }
                }
            }
            EditorBottomPanel(model, onStageMenu = { dialog = EditorDialog.STAGE })
        }
        when (dialog) {
            EditorDialog.LEVEL -> LevelSettingsDialog(model, onClose = { dialog = null })
            EditorDialog.STAGE -> StageDialog(model, onDelete = { dialog = EditorDialog.DELETE_STAGE }, onClose = { dialog = null })
            EditorDialog.DELETE_STAGE -> ConfirmDialog(
                stringResource(R.string.editor_delete_stage_confirm, model.active + 1),
                onConfirm = {
                    val k = model.active
                    model.reshape(focus = (k - 1).coerceAtLeast(0)) { UserLevelEdits.deleteStage(it, k) }
                    dialog = null
                },
                onCancel = { dialog = EditorDialog.STAGE },
            )
            EditorDialog.PIECE -> PieceDialog(model, onClose = {
                model.selected = null
                dialog = null
            })
            EditorDialog.TEST -> TestDialog(model, onClose = { dialog = null }) { from ->
                dialog = null
                onPlay(model.draft, EditorRun.Test(from))
            }
            EditorDialog.PUBLISH -> PublishDialog(
                model,
                onVerify = {
                    dialog = null
                    onPlay(model.draft, EditorRun.Verify)
                },
                onAccount = onAccount,
                onClose = { dialog = null },
            )
            null -> Unit
        }
    }
}

/** Otwarte niedawno szkice (stan kreatora z historią cofania) – na czas tego uruchomienia gry. */
private val sessions = LinkedHashMap<String, EditorModel>()
private const val MAX_SESSIONS = 4

@Composable
private fun EditorTopBar(model: EditorModel, onBack: () -> Unit, onDialog: (EditorDialog) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = px(4), vertical = px(3)),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelButton("<", onClick = onBack, style = ButtonStyle.WOOD, size = 3)
            HSpace(4)
            Column(Modifier.weight(1f).noRippleClick { onDialog(EditorDialog.LEVEL) }) {
                val title = model.level.title.ifBlank { stringResource(R.string.editor_untitled) }
                PixelText(title, size = if (title.length > LONG_EDITOR_TITLE) 2 else 3, maxLines = 1)
                val estimate = remember(model.draft) { UserLevelRating.estimate(model.level) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TierIcon(estimate.tier, 1)
                    HSpace(2)
                    PixelText(tierName(estimate.tier), size = 1, color = tierColor(estimate.tier))
                    HSpace(4)
                    PixelText(
                        when {
                            model.draft.published -> stringResource(R.string.creator_state_published, model.draft.publishedId ?: 0)
                            model.draft.verified -> stringResource(R.string.editor_ready)
                            else -> stringResource(R.string.creator_state_draft)
                        },
                        size = 1,
                        color = if (model.draft.verified || model.draft.published) Pal.GreenLight else Pal.Foam,
                        maxLines = 1,
                    )
                }
            }
            HSpace(4)
            PixelButton(stringResource(R.string.editor_test), onClick = { onDialog(EditorDialog.TEST) }, style = ButtonStyle.GREEN, size = 3)
        }
        VSpace(3)
        ButtonRow(gap = 3) {
            PixelButton(stringResource(R.string.editor_undo), onClick = { model.undo() }, style = ButtonStyle.SEA, size = 1, enabled = model.canUndo)
            PixelButton(stringResource(R.string.editor_redo), onClick = { model.redo() }, style = ButtonStyle.SEA, size = 1, enabled = model.canRedo)
            PixelButton(stringResource(R.string.editor_settings), onClick = { onDialog(EditorDialog.LEVEL) }, style = ButtonStyle.WOOD, size = 1)
            PixelButton(
                stringResource(R.string.editor_publish),
                onClick = { onDialog(EditorDialog.PUBLISH) },
                style = if (model.problems.any { it.error }) ButtonStyle.RED else ButtonStyle.ORANGE,
                size = 1,
            )
        }
    }
}

@Composable
private fun EditorCanvas(model: EditorModel, onPiece: (PieceRef) -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    val renderer = remember { EditorRenderer() }
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) withFrameNanos { now ->
            if (last != 0L) time += ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
        }
    }
    val slop = LocalViewConfiguration.current.touchSlop
    val stageLabel = stringResource(R.string.hud_stage)
    val quotaLabel = stringResource(R.string.editor_quota)
    Canvas(
        Modifier
            .fillMaxSize()
            // Plansza rysuje się na płótnie Androida – bez przycięcia zamalowałaby paski kreatora.
            .clipToBounds()
            .pointerInput(model) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tool = model.tool
                    var moved = false
                    var multi = false
                    val drag = if (tool == EditorTool.MOVE) model.pieceAt(down.position.x, down.position.y) else null
                    if (drag != null) {
                        model.dragRef = drag
                        model.dragX = model.worldX(down.position.x)
                        model.dragY = model.worldY(down.position.y)
                    }
                    if (tool.paints) {
                        model.beginStroke()
                        model.paint(down.position.x, down.position.y)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            // Dwa palce zawsze przewijają planszę.
                            multi = true
                            model.brush = emptyList()
                            model.dragRef = null
                            val dy = pressed.map { it.position.y - it.previousPosition.y }.average().toFloat()
                            model.scrollBy(dy)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > slop) moved = true
                        when {
                            multi -> Unit
                            tool.paints -> model.paint(change.position.x, change.position.y)
                            drag != null -> {
                                model.dragX = model.worldX(change.position.x)
                                model.dragY = model.worldY(change.position.y)
                            }
                            moved -> model.scrollBy(change.position.y - change.previousPosition.y)
                        }
                        change.consume()
                    }
                    val up = down.position
                    when {
                        tool.paints -> model.endStroke()
                        multi -> Unit
                        drag != null -> {
                            model.dragRef = null
                            if (moved) model.drop(drag, model.dragX * model.scale + model.offsetX, (model.dragY - model.scroll) * model.scale)
                            else onPiece(drag)
                        }
                        moved -> Unit
                        tool == EditorTool.DOOR -> model.moveDoor(up.x, up.y)
                        tool.placement != null -> if (model.place(up.x, up.y)) services.audio.play(Sfx.CLICK)
                        tool == EditorTool.MOVE -> model.cellAt(up.x, up.y)?.let { model.active = it.first }
                    }
                }
            },
    ) {
        model.viewHeight = size.height
        time
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            val level = model.level
            val built = model.built
            if (model.scroll < 0f) {
                model.scale = renderer.scaleFor(size.width.toInt()).toFloat()
                model.clampScroll()
            }
            val problems = model.problems.filter { it.error && it.col >= 0 && it.stage >= 0 }
                .map { built.layout.stages[it.stage].topRow + it.row to it.col }
            val drag = model.dragRef
            val selected = model.selected?.let { ref -> pieceCell(level, ref)?.let { (c0, r0) -> built.layout.stages[ref.stage].topRow + r0 to c0 } }
            renderer.draw(
                canvas, level, built, size.width.toInt(), size.height.toInt(), model.scroll, time, look,
                EditorOverlay(
                    selected = selected,
                    brush = model.brush,
                    problems = problems,
                    dragX = model.dragX,
                    dragY = model.dragY,
                    dragging = drag?.let { pieceSprite(level, it) },
                    activeStage = model.active,
                ),
            ) { k ->
                val s = level.stages[k]
                stageLabel.format(k + 1, level.stages.size) + " · " + quotaLabel + " " + s.quota + "/" + s.fishTotal
            }
            model.scale = renderer.scale
            model.offsetX = renderer.offsetX
        }
    }
}

private fun pieceCell(level: UserLevel, ref: PieceRef): Pair<Int, Int>? {
    val s = level.stages.getOrNull(ref.stage) ?: return null
    return when (ref.piece) {
        Piece.FISH -> s.fish.getOrNull(ref.index)?.let { it.col to it.row }
        Piece.GOLDEN -> s.golden.getOrNull(ref.index)?.let { it.col to it.row }
        Piece.ENEMY -> s.enemies.getOrNull(ref.index)?.let { it.col to it.row }
        Piece.CRAB -> s.crabs.getOrNull(ref.index)?.let { it.col to it.row }
    }
}

private fun pieceSprite(level: UserLevel, ref: PieceRef): android.graphics.Bitmap? {
    val s = level.stages.getOrNull(ref.stage) ?: return null
    return when (ref.piece) {
        Piece.FISH -> Sprites.sprite(if (s.fish.getOrNull(ref.index)?.blue == true) "fishblue1" else "fish1")
        Piece.GOLDEN -> Sprites.sprite("fishgold1")
        Piece.ENEMY -> s.enemies.getOrNull(ref.index)?.let { Sprites.sprite(enemyIcon(it.kind)) }
        Piece.CRAB -> Sprites.tiles(Worlds.theme(level.theme)).crab
    }
}

/** Ścieżka sprite'a ikony narzędzia (kafelki w barwach poziomu). */
private fun toolIcon(tool: EditorTool, theme: String): String = when (tool) {
    EditorTool.MOVE -> "sprites/doigt"
    EditorTool.SAND -> "tiles/$theme/sandfull"
    EditorTool.ROCK -> "tiles/$theme/rock0"
    EditorTool.ERASE -> "tiles/$theme/wave0"
    EditorTool.FISH -> "sprites/fish1"
    EditorTool.BLUE -> "sprites/fishblue1"
    EditorTool.GOLDEN -> "sprites/fishgold1"
    EditorTool.CRAB -> "tiles/$theme/crab"
    EditorTool.DOOR -> "tiles/$theme/door0"
    else -> "sprites/" + enemyIcon(tool.enemy!!)
}

@Composable
private fun toolHint(tool: EditorTool): String = when (tool) {
    EditorTool.MOVE -> stringResource(R.string.editor_tool_move)
    EditorTool.SAND -> stringResource(R.string.editor_tool_sand)
    EditorTool.ROCK -> stringResource(R.string.editor_tool_rock)
    EditorTool.ERASE -> stringResource(R.string.editor_tool_erase)
    EditorTool.FISH -> stringResource(R.string.editor_tool_fish)
    EditorTool.BLUE -> stringResource(R.string.editor_tool_blue)
    EditorTool.GOLDEN -> stringResource(R.string.editor_tool_golden)
    EditorTool.CRAB -> stringResource(R.string.editor_tool_crab)
    EditorTool.DOOR -> stringResource(R.string.editor_tool_door)
    else -> stringResource(R.string.editor_tool_enemy, enemyName(tool.enemy!!), enemyDescription(tool.enemy))
}

@Composable
private fun EditorBottomPanel(model: EditorModel, onStageMenu: () -> Unit) {
    val level = model.level
    val k = model.active.coerceIn(0, level.stages.lastIndex)
    val stage = level.stages[k]
    val theme = Worlds.theme(UserLevels.themeOf(level, stage))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.Navy)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = px(4), vertical = px(3)),
    ) {
        PixelText(toolHint(model.tool), size = 1, color = Pal.Foam, maxLines = 3, modifier = Modifier.fillMaxWidth())
        VSpace(3)
        // Wybór etapu strzałkami – plansza sama przewija się do wybranego etapu.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(3))) {
            StageArrow(up = false, enabled = k > 0, modifier = Modifier.fillMaxHeight()) { model.focusStage(k - 1) }
            PixelText(
                stringResource(R.string.hud_stage, k + 1, level.stages.size),
                size = 2,
                color = Pal.Gold,
                align = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            StageArrow(up = true, enabled = k < level.stages.lastIndex, modifier = Modifier.fillMaxHeight()) { model.focusStage(k + 1) }
            PixelButton(stringResource(R.string.editor_stage_menu), onClick = onStageMenu, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        VSpace(3)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(px(4))) {
            Stepper(stringResource(R.string.editor_rows), stage.rows.toString(), Modifier.weight(1f),
                onMinus = { model.reshape { UserLevelEdits.setRows(it, k, it.stages[k].rows - 1) } },
                onPlus = { model.reshape { UserLevelEdits.setRows(it, k, it.stages[k].rows + 1) } })
            Stepper(stringResource(R.string.editor_quota), "${stage.quota}/${stage.fishTotal}", Modifier.weight(1f),
                onMinus = { model.edit { UserLevelEdits.setQuota(it, k, it.stages[k].quota - 1) } },
                onPlus = { model.edit { UserLevelEdits.setQuota(it, k, it.stages[k].quota + 1) } })
        }
        VSpace(3)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(px(2))) {
            for (tool in EditorTool.entries) {
                val image = rememberSprite(toolIcon(tool, theme))
                PixelButton(
                    text = "",
                    onClick = { model.tool = tool },
                    style = if (model.tool == tool) ButtonStyle.GREEN else ButtonStyle.SEA,
                    modifier = Modifier.width(px(TOOL_SIZE)),
                ) {
                    Box(Modifier.size(px(20)), contentAlignment = Alignment.Center) {
                        SpriteImage(image, if (image.width > 16) 1 else if (image.width > 9) 1 else 2)
                    }
                }
            }
        }
        VSpace(2)
        PixelText(stringResource(R.string.editor_pan_hint), size = 1, color = Pal.SeaLight, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Strzałka wyboru etapu: w górę – bliżej mety. */
@Composable
private fun StageArrow(up: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PixelButton("", onClick = onClick, style = ButtonStyle.SEA, enabled = enabled, modifier = modifier) {
        SpriteImage("arrowup", 1, Modifier.rotate(if (up) 0f else 180f))
    }
}

private const val TOOL_SIZE = MIN_BUTTON

/** Dłuższe nazwy poziomu w pasku kreatora mniejszą czcionką (cała nazwa ma się zmieścić). */
private const val LONG_EDITOR_TITLE = 13

/** Mała liczba z przyciskami − i +. */
@Composable
private fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(label, size = 1, color = Pal.SeaLight, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton("-", onClick = onMinus, style = ButtonStyle.SEA, size = 2)
            PixelText(value, size = 2, align = TextAlign.Center, maxLines = 1, modifier = Modifier.width(px(40)))
            PixelButton("+", onClick = onPlus, style = ButtonStyle.SEA, size = 2)
        }
    }
}

/** Stopień trudności jako potwór: od ryby (spacer) po widmokudę z czerwonymi oczami (demon). */
fun tierIcon(tier: Int): String = when (tier.coerceIn(0, UserLevelRating.TIERS - 1)) {
    0 -> "fish1"
    1 -> "piranha1"
    2 -> "octopus1"
    3 -> "shark1"
    4 -> "octopusred1"
    5 -> "sharkdark1"
    else -> "stalkerlock1"
}

@Composable
fun TierIcon(tier: Int, scale: Int) {
    Box(Modifier.size(px(24 * scale), px(14 * scale)), contentAlignment = Alignment.Center) {
        SpriteImage(tierIcon(tier), if (tier >= 3 && scale > 1) scale - 1 else scale)
    }
}

@Composable
fun tierName(tier: Int): String = stringResource(
    when (tier.coerceIn(0, UserLevelRating.TIERS - 1)) {
        0 -> R.string.tier_0
        1 -> R.string.tier_1
        2 -> R.string.tier_2
        3 -> R.string.tier_3
        4 -> R.string.tier_4
        5 -> R.string.tier_5
        else -> R.string.tier_6
    },
)

fun tierColor(tier: Int): Color = when (tier) {
    0 -> Pal.SeaLight
    1 -> Pal.GreenLight
    2 -> Pal.Green
    3 -> Pal.Sand
    4 -> Pal.Orange
    5 -> Pal.RedLight
    else -> Pal.PurpleLight
}

// ------------------------------------------------------------------ okienka

/** Wybór palety: 10 palet światów i złota paleta finału ([withSame] – także „jak poziom”). */
@Composable
private fun PalettePicker(current: Int, withSame: Boolean, onPick: (Int) -> Unit) {
    val names = rememberWorldNames()
    val options = (if (withSame) listOf(UserLevels.SAME_THEME) else emptyList()) + listOf(Worlds.FINALE) + (0..9)
    for (row in options.chunked(6)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(2))) {
            for (theme in row) PaletteSwatch(theme, theme == current, Modifier.weight(1f)) { onPick(theme) }
            repeat(6 - row.size) { Box(Modifier.weight(1f)) }
        }
        VSpace(2)
    }
    PixelText(
        if (current == UserLevels.SAME_THEME) stringResource(R.string.editor_same_palette) else names(current),
        size = 1,
        color = Pal.Gold,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PaletteSwatch(theme: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val u = LocalUnit.current.toFloat()
    val audio = LocalServices.current.audio
    val name = if (theme == UserLevels.SAME_THEME) null else Worlds.theme(theme)
    val sea = name?.let { rememberSprite("tiles/$it/sea") }
    val sand = name?.let { rememberSprite("tiles/$it/sandleft0") }
    Box(
        modifier
            .height(px(30))
            .drawBehind {
                drawRect(if (selected) Pal.Gold else Pal.Outline)
                val b = if (selected) 2 * u else u
                if (sea != null && sand != null) {
                    val k = ((size.height - 2 * b) / sea.height)
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(b, b, size.width - b, size.height - b)
                    var x = b
                    while (x < size.width) {
                        drawSprite(if (x == b) sand else sea, Offset(x, b), k)
                        x += sea.width * k
                    }
                    drawContext.canvas.restore()
                } else {
                    drawRect(Pal.SeaDark, Offset(b, b), Size(size.width - 2 * b, size.height - 2 * b))
                }
            }
            .noRippleClick {
                audio.play(Sfx.CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (theme == UserLevels.SAME_THEME) PixelText("=", size = 2)
    }
}

/** Wiersz z etykietą i małym licznikiem. */
@Composable
private fun SettingStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PixelText(label, size = 1, color = Pal.Foam, modifier = Modifier.weight(1f))
        PixelButton("-", onClick = onMinus, style = ButtonStyle.SEA, size = 2)
        PixelText(value, size = 2, align = TextAlign.Center, modifier = Modifier.width(px(60)))
        PixelButton("+", onClick = onPlus, style = ButtonStyle.SEA, size = 2)
    }
}

/** Rząd przycisków wyboru jednej z opcji. */
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
    ButtonRow(gap = 2) {
        for ((value, label) in options) {
            PixelButton(label, onClick = { onPick(value) }, style = if (value == current) ButtonStyle.GREEN else ButtonStyle.SEA, size = 1)
        }
    }
}

@Composable
private fun LevelSettingsDialog(model: EditorModel, onClose: () -> Unit) {
    val level = model.level
    var title by remember { mutableStateOf(level.title) }
    val commitTitle = {
        val clean = UserLevels.normalizeTitle(title).filter { it.isLetterOrDigit() || it in " _.,-!?'():&#" }.take(UserLevels.TITLE_MAX)
        if (clean != level.title) model.set(level.copy(title = clean))
    }
    Overlay(onDismiss = {
        commitTitle()
        onClose()
    }) {
        PixelText(stringResource(R.string.editor_level_title), size = 3, color = Pal.WoodDark, outline = null)
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelTextField(title, { title = it }, stringResource(R.string.editor_name), maxLength = UserLevels.TITLE_MAX, imeAction = ImeAction.Done, onDone = commitTitle)
                VSpace(4)
                PixelText(stringResource(R.string.editor_palette), size = 1, color = Pal.Foam)
                VSpace(2)
                PalettePicker(level.theme, withSame = false) { t -> model.edit { it.copy(theme = t) } }
                VSpace(4)
                SettingStepper(stringResource(R.string.editor_speed), "${level.speed}%",
                    onMinus = { model.edit { it.copy(speed = (it.speed - 10).coerceAtLeast(UserLevels.MIN_SPEED)) } },
                    onPlus = { model.edit { it.copy(speed = (it.speed + 10).coerceAtMost(UserLevels.MAX_SPEED)) } })
                VSpace(2)
                val par = model.built.plan.spec.parSeconds
                SettingStepper(
                    stringResource(R.string.editor_time),
                    if (level.time == 0) stringResource(R.string.editor_time_off) else formatTime(level.time),
                    onMinus = { model.edit { it.copy(time = if (it.time <= UserLevels.MIN_TIME) 0 else it.time - 10) } },
                    onPlus = {
                        model.edit { it.copy(time = if (it.time == 0) maxOf(UserLevels.MIN_TIME, (par * 135 + 99) / 100 / 10 * 10) else (it.time + 10).coerceAtMost(UserLevels.MAX_TIME)) }
                    },
                )
                PixelText(stringResource(R.string.editor_par, formatTime(par)), size = 1, color = Pal.SeaLight)
            }
        }
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelText(stringResource(R.string.editor_goal2), size = 1, color = Pal.Foam)
                VSpace(2)
                Choice(
                    listOf(SecondGoal.NO_DAMAGE to stringResource(R.string.editor_goal_nodamage), SecondGoal.ALL_FISH to stringResource(R.string.editor_goal_allfish)),
                    level.secondGoal,
                ) { g -> model.edit { it.copy(secondGoal = g) } }
                VSpace(4)
                PixelText(stringResource(R.string.editor_goal3), size = 1, color = Pal.Foam)
                VSpace(2)
                Choice(
                    listOf(
                        ThirdGoal.PAR_TIME to stringResource(R.string.editor_goal_par),
                        ThirdGoal.SCORE to stringResource(R.string.editor_goal_score),
                        ThirdGoal.ALL_GOLDEN to stringResource(R.string.editor_goal_golden),
                        ThirdGoal.COMBO to stringResource(R.string.editor_goal_combo),
                    ),
                    level.thirdGoal,
                ) { g -> model.edit { it.copy(thirdGoal = g) } }
                if (level.thirdGoal == ThirdGoal.COMBO) {
                    VSpace(2)
                    SettingStepper(stringResource(R.string.editor_combo), "x${level.combo}",
                        onMinus = { model.edit { it.copy(combo = (it.combo - 1).coerceAtLeast(2)) } },
                        onPlus = { model.edit { it.copy(combo = (it.combo + 1).coerceAtMost(5)) } })
                }
                VSpace(3)
                for (goal in goalTexts(model.built.plan.spec)) PixelText("· $goal", size = 1, color = Pal.SeaLight)
            }
        }
        VSpace(4)
        DifficultyCard(level)
        VSpace(6)
        PixelButton(stringResource(R.string.close), onClick = {
            commitTitle()
            onClose()
        }, style = ButtonStyle.GREEN, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
    }
}

@Composable
private fun DifficultyCard(level: UserLevel) {
    val estimate = remember(level) { UserLevelRating.estimate(level) }
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(stringResource(R.string.editor_difficulty), size = 1, color = Pal.Gold)
            VSpace(2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierIcon(estimate.tier, 2)
                HSpace(4)
                Column {
                    PixelText(tierName(estimate.tier), size = 3, color = tierColor(estimate.tier))
                    PixelText(stringResource(R.string.editor_points_info, UserLevelRating.POINTS[estimate.tier]), size = 1, color = Pal.Foam)
                }
            }
            VSpace(2)
            PixelText(stringResource(R.string.editor_difficulty_info), size = 1, color = Pal.SeaLight)
        }
    }
}

@Composable
private fun StageDialog(model: EditorModel, onDelete: () -> Unit, onClose: () -> Unit) {
    val level = model.level
    val k = model.active.coerceIn(0, level.stages.lastIndex)
    val stage = level.stages[k]
    val many = level.stages.size > 1
    val room = level.stages.size < UserLevels.MAX_STAGES
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.editor_stage_title, k + 1), size = 4, color = Pal.WoodDark, outline = null)
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelText(stringResource(R.string.editor_stage_palette), size = 1, color = Pal.Foam)
                VSpace(2)
                PalettePicker(stage.theme, withSame = true) { t -> model.edit { UserLevelEdits.setStage(it, k) { s -> s.copy(theme = t) } } }
                VSpace(4)
                PixelText(stringResource(R.string.editor_current), size = 1, color = Pal.Foam)
                VSpace(2)
                SettingStepper(stringResource(R.string.editor_current_x), stage.currentX.toString(),
                    onMinus = { model.set(UserLevelEdits.setStage(model.level, k) { it.copy(currentX = (it.currentX - 5).coerceAtLeast(-UserLevels.MAX_CURRENT)) }) },
                    onPlus = { model.set(UserLevelEdits.setStage(model.level, k) { it.copy(currentX = (it.currentX + 5).coerceAtMost(UserLevels.MAX_CURRENT)) }) })
                VSpace(2)
                SettingStepper(stringResource(R.string.editor_current_y), stage.currentY.toString(),
                    onMinus = { model.set(UserLevelEdits.setStage(model.level, k) { it.copy(currentY = (it.currentY - 5).coerceAtLeast(-UserLevels.MAX_CURRENT)) }) },
                    onPlus = { model.set(UserLevelEdits.setStage(model.level, k) { it.copy(currentY = (it.currentY + 5).coerceAtMost(UserLevels.MAX_CURRENT)) }) })
            }
        }
        VSpace(4)
        // Każda zmiana liczy od bieżącego etapu (model.active) i przewija planszę do etapu, który się zmienił.
        StageButtons(
            ButtonStyle.GREEN,
            R.string.editor_add_below to room,
            R.string.editor_add_above to room,
        ) { i ->
            val at = model.active
            if (i == 0) model.reshape(focus = at) { UserLevelEdits.addStage(it, at) }
            else model.reshape(focus = at + 1) { UserLevelEdits.addStage(it, at + 1) }
        }
        VSpace(3)
        StageButtons(
            ButtonStyle.SEA,
            R.string.editor_duplicate to room,
            R.string.editor_mirror to true,
            R.string.editor_clear to true,
        ) { i ->
            val at = model.active
            when (i) {
                0 -> model.reshape(focus = at + 1) { UserLevelEdits.duplicateStage(it, at) }
                // Odbijamy etap po poprawkach: ukryty piasek z pól przy wrotach nie może wyjść w innym miejscu.
                1 -> model.reshape(focus = at) { raw ->
                    raw.copy(stages = raw.stages.toMutableList().also { it[at] = UserLevelEdits.mirrorStage(model.level, at).stages[at] })
                }
                else -> model.reshape(focus = at) { UserLevelEdits.clearStage(it, at) }
            }
        }
        VSpace(3)
        StageButtons(
            ButtonStyle.WOOD,
            R.string.editor_move_down to (k > 0),
            R.string.editor_move_up to (k < level.stages.lastIndex),
        ) { i ->
            val at = model.active
            val up = i == 1
            model.reshape(focus = if (up) at + 1 else at - 1) { UserLevelEdits.moveStage(it, at, up) }
        }
        VSpace(3)
        PixelButton(stringResource(R.string.editor_delete_stage), onClick = onDelete, enabled = many, style = ButtonStyle.RED, size = 2, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.GREEN, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
    }
}

/** Rząd przycisków okna etapu o równej wysokości: napis i czy przycisk działa; [onClick] dostaje numer przycisku. */
@Composable
private fun StageButtons(style: ButtonStyle, vararg buttons: Pair<Int, Boolean>, onClick: (Int) -> Unit) {
    ButtonRow(gap = 3) {
        for ((i, button) in buttons.withIndex()) {
            PixelButton(
                stringResource(button.first),
                onClick = { onClick(i) },
                enabled = button.second,
                style = style,
                size = 1,
            )
        }
    }
}

@Composable
private fun PieceDialog(model: EditorModel, onClose: () -> Unit) {
    val ref = model.selected
    val level = model.level
    val stage = ref?.let { level.stages.getOrNull(it.stage) }
    if (ref == null || stage == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    Overlay(onDismiss = onClose) {
        when (ref.piece) {
            Piece.FISH -> {
                val fish = stage.fish.getOrNull(ref.index) ?: return@Overlay
                SpriteImage(if (fish.blue) "fishblue1" else "fish1", 4)
                VSpace(4)
                Choice(
                    listOf(false to stringResource(R.string.editor_fish_common), true to stringResource(R.string.editor_fish_blue)),
                    fish.blue,
                ) { model.set(UserLevelEdits.setFishBlue(model.level, ref, it)) }
            }
            Piece.GOLDEN -> {
                val g = stage.golden.getOrNull(ref.index) ?: return@Overlay
                SpriteImage("fishgold1", 4)
                VSpace(4)
                SettingStepper(stringResource(R.string.editor_delay), stringResource(R.string.editor_seconds, g.delay),
                    onMinus = { model.edit { UserLevelEdits.setGoldenDelay(it, ref, it.stages[ref.stage].golden[ref.index].delay - 1) } },
                    onPlus = { model.edit { UserLevelEdits.setGoldenDelay(it, ref, it.stages[ref.stage].golden[ref.index].delay + 1) } })
            }
            Piece.ENEMY -> {
                val e = stage.enemies.getOrNull(ref.index) ?: return@Overlay
                SpriteImage(enemyIcon(e.kind), 3)
                VSpace(2)
                PixelText(enemyName(e.kind), size = 3, color = Pal.WoodDark, outline = null)
                PixelText(enemyDescription(e.kind), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(4)
                PixelText(stringResource(R.string.editor_dir), size = 1, color = Pal.WoodDark, outline = null)
                VSpace(2)
                val options = buildList {
                    add(-1 to stringResource(R.string.editor_dir_random))
                    for (d in listOf(2, 0, 3, 1)) {
                        if (UserLevels.validDir(e.kind, d) == d) {
                            add(d to stringResource(when (d) {
                                0 -> R.string.editor_dir_right
                                1 -> R.string.editor_dir_down
                                2 -> R.string.editor_dir_left
                                else -> R.string.editor_dir_up
                            }))
                        }
                    }
                }
                Choice(options, e.dir) { model.set(UserLevelEdits.setEnemyDir(model.level, ref, it)) }
            }
            Piece.CRAB -> SpriteImage(Sprites.tiles(Worlds.theme(level.theme)).crab.let { rememberSprite(it) }, 4)
        }
        VSpace(6)
        ButtonRow(gap = 4) {
            PixelButton(stringResource(R.string.creator_delete), onClick = {
                model.set(UserLevelEdits.remove(model.level, ref))
                onClose()
            }, style = ButtonStyle.RED, size = 2)
            PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.GREEN, size = 2, modifier = Modifier.keyPrimary())
        }
    }
}

@Composable
private fun TestDialog(model: EditorModel, onClose: () -> Unit, onStart: (Int) -> Unit) {
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.editor_test_title), size = 4, color = Pal.WoodDark, outline = null)
        VSpace(3)
        PixelText(stringResource(R.string.editor_test_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        PixelButton(stringResource(R.string.editor_test_from_start), onClick = { onStart(0) }, style = ButtonStyle.GREEN, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
        if (model.level.stages.size > 1 && model.active > 0) {
            VSpace(3)
            PixelButton(stringResource(R.string.editor_test_from_stage, model.active + 1), onClick = { onStart(model.active) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        }
        VSpace(3)
        PixelButton(stringResource(R.string.cancel), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun problemText(p: UserLevelProblem): String = when (p.kind) {
    UserLevelProblem.Kind.DOOR_UNREACHABLE -> stringResource(R.string.problem_door, p.stage + 1)
    UserLevelProblem.Kind.QUOTA_TOO_HIGH -> stringResource(R.string.problem_quota, p.stage + 1)
    UserLevelProblem.Kind.QUOTA_NEEDS_GOLDEN -> stringResource(R.string.problem_quota_golden, p.stage + 1)
    UserLevelProblem.Kind.FISH_UNREACHABLE -> stringResource(R.string.problem_fish, p.stage + 1)
    UserLevelProblem.Kind.ENEMY_TRAPPED -> stringResource(R.string.problem_enemy, p.stage + 1)
    UserLevelProblem.Kind.NO_GOLDEN -> stringResource(R.string.problem_golden)
    UserLevelProblem.Kind.TITLE_SHORT -> stringResource(R.string.problem_title)
}

@Composable
private fun PublishDialog(model: EditorModel, onVerify: () -> Unit, onAccount: () -> Unit, onClose: () -> Unit) {
    val services = LocalServices.current
    val online = services.online
    val draft = model.draft
    val problems = model.problems
    val errors = problems.filter { it.error }.distinctBy { it.kind to it.stage }
    val warnings = problems.filter { !it.error }.distinctBy { it.kind to it.stage }
    var copyable by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    val scope = rememberCoroutineScope()
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.editor_publish_title), size = 4, color = Pal.WoodDark, outline = null)
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                if (errors.isEmpty() && warnings.isEmpty()) {
                    PixelText(stringResource(R.string.editor_no_problems), size = 2, color = Pal.GreenLight)
                }
                if (errors.isNotEmpty()) {
                    PixelText(stringResource(R.string.editor_problems), size = 1, color = Pal.RedLight)
                    for (p in errors) PixelText("· " + problemText(p), size = 1, color = Pal.White)
                    VSpace(3)
                }
                if (warnings.isNotEmpty()) {
                    PixelText(stringResource(R.string.editor_warnings), size = 1, color = Pal.Sand)
                    for (p in warnings) PixelText("· " + problemText(p), size = 1, color = Pal.Foam)
                }
            }
        }
        VSpace(4)
        DifficultyCard(model.level)
        VSpace(4)
        when {
            draft.published -> {
                PixelText(stringResource(R.string.editor_published, draft.publishedId ?: 0), size = 2, color = Pal.GreenDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            !draft.verified -> {
                PixelText(stringResource(R.string.editor_verify_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(4)
                PixelButton(stringResource(R.string.editor_verify), onClick = onVerify, enabled = errors.isEmpty(), style = ButtonStyle.GREEN, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
            }
            else -> {
                PixelText(stringResource(R.string.editor_verified), size = 2, color = Pal.GreenDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(3)
                PixelText(stringResource(R.string.editor_publish_rules, COMMUNITY_MIN_PLAYERS), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(3)
                if (!online.loggedIn) {
                    PixelText(stringResource(R.string.editor_publish_login), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    VSpace(3)
                    PixelButton(stringResource(R.string.account_title), onClick = onAccount, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
                } else {
                    CheckRow(copyable, stringResource(R.string.editor_copyable)) { copyable = !copyable }
                    VSpace(3)
                    PixelButton(
                        stringResource(R.string.editor_publish_go),
                        onClick = {
                            if (!busy) {
                                busy = true
                                error = null
                                scope.launch {
                                    when (val res = online.community.publish(draft, copyable)) {
                                        is Net.Ok -> {
                                            model.updateDraft(model.draft.with(publishedId = res.value.id, publishedHash = model.draft.hash))
                                            services.audio.play(Sfx.WIN)
                                        }
                                        is Net.Err -> error = res.failure
                                    }
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy && errors.isEmpty(),
                        style = ButtonStyle.ORANGE,
                        size = 3,
                        modifier = Modifier.keyPrimary().fillMaxWidth(),
                    )
                }
            }
        }
        error?.let {
            VSpace(3)
            PixelText(onlineErrorText(it), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (busy) {
            VSpace(3)
            PixelText(stringResource(R.string.online_loading), size = 1, color = Pal.WoodDark, outline = null)
        }
        VSpace(5)
        // Link dla znajomych – bez wymagań publikacji (przejścia poziomu, konta, poprawek planszy).
        val share = rememberLinkShare()
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(stringResource(R.string.share_title), size = 2, color = Pal.Gold)
                VSpace(2)
                PixelText(
                    if (draft.published) stringResource(R.string.share_info_published, draft.publishedId ?: 0) else stringResource(R.string.share_info_draft),
                    size = 1,
                    color = Pal.Foam,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(3)
                PixelButton(
                    stringResource(R.string.share_go),
                    onClick = { shareDraft(share, online, draft) },
                    enabled = !share.busy,
                    style = ButtonStyle.SEA,
                    size = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                share.error?.let {
                    VSpace(2)
                    PixelText(onlineErrorText(it), size = 1, color = Pal.RedLight, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        VSpace(6)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Pole wyboru na piaskowym panelu: cały wiersz to ciemna tabliczka (jak [DarkCard]) wysokości przycisku,
 * po zaznaczeniu ze złotą krawędzią i zielonym polem z ptaszkiem.
 */
@Composable
fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    val u = LocalUnit.current.toFloat()
    Row(
        Modifier
            .fillMaxWidth()
            .noRippleClick(onToggle)
            .drawBehind {
                pixelBox(u, if (checked) Pal.Navy else Pal.SeaDark, if (checked) Pal.Gold else Pal.Sea, Pal.Outline, shadow = 1f)
            }
            .heightIn(min = px(MIN_BUTTON))
            .padding(horizontal = px(6), vertical = px(4)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(px(14)).drawBehind { checkBox(u, checked) })
        HSpace(5)
        PixelText(label, size = 1, color = if (checked) Pal.Gold else Pal.Foam, modifier = Modifier.weight(1f))
    }
}

/** Ptaszek w polu 14×14 – kratki 2×2 (lewy górny róg, w pikselach interfejsu). */
private val CHECK_MARK = listOf(3 to 6, 4 to 7, 5 to 8, 6 to 7, 7 to 6, 8 to 5, 9 to 4, 10 to 3)

private fun DrawScope.checkBox(u: Float, checked: Boolean) {
    if (!checked) {
        // Puste pole „wciśnięte” w tabliczkę: ciemniejsza krawędź u góry zamiast jasnej.
        pixelBox(u, Pal.Foam, Pal.GreyLight, Pal.Foam, shadow = 0f)
        return
    }
    pixelBox(u, Pal.Green, Pal.GreenLight, Pal.GreenDark, shadow = 1f)
    for ((x, y) in CHECK_MARK) drawRect(Pal.GreenDark, Offset(x * u, (y + 1) * u), Size(2 * u, 2 * u))
    for ((x, y) in CHECK_MARK) drawRect(Pal.White, Offset(x * u, y * u), Size(2 * u, 2 * u))
}
