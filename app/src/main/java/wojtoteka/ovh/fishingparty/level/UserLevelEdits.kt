package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH

/** Rodzaj postaci na planszy kreatora. */
enum class Piece { FISH, GOLDEN, ENEMY, CRAB }

/** Postać na planszy kreatora: etap, rodzaj i miejsce na liście etapu. */
data class PieceRef(val stage: Int, val piece: Piece, val index: Int)

/** Co stawia narzędzie kreatora. */
sealed interface Placement {
    data class Fish(val blue: Boolean) : Placement
    data object Golden : Placement
    data class Enemy(val kind: EnemyKind) : Placement
    data object Crab : Placement
}

/**
 * Zmiany poziomu w kreatorze – każda zwraca nowy poziom (stary zostaje do cofania). Kafelki i postacie
 * po zmianie trzeba jeszcze przepuścić przez [UserLevels.normalize] (robi to kreator).
 */
object UserLevelEdits {

    private fun UserLevel.withStage(k: Int, change: (UserStage) -> UserStage): UserLevel =
        copy(stages = stages.mapIndexed { i, s -> if (i == k) change(s) else s })

    /** Maluje kafelek [code] na polach etapu (pola zawsze z wodą – przy wrotach i na starcie – pomija). */
    fun paint(level: UserLevel, k: Int, cells: List<Pair<Int, Int>>, code: Byte): UserLevel {
        val stage = level.stages[k]
        val next = stage.cells.copyOf()
        var changed = false
        for ((c, r) in cells) {
            if (c !in 0 until WIDTH || r !in 0 until stage.rows || UserLevels.locked(level, k, c, r)) continue
            if (next[r * WIDTH + c] != code) {
                next[r * WIDTH + c] = code
                changed = true
            }
        }
        // Woda „gumką” zabiera też postać z pola.
        val erase = if (code == UserLevels.SEA) cells.toSet() else emptySet()
        if (!changed && erase.none { pieceAt(level, k, it.first, it.second) != null }) return level
        return level.withStage(k) { s ->
            s.copy(
                cells = next,
                fish = s.fish.filter { (it.col to it.row) !in erase },
                golden = s.golden.filter { (it.col to it.row) !in erase },
                enemies = s.enemies.filter { (it.col to it.row) !in erase },
                crabs = s.crabs.filter { (it.col to it.row) !in erase },
            )
        }
    }

    fun pieceAt(level: UserLevel, k: Int, col: Int, row: Int): PieceRef? {
        val s = level.stages.getOrNull(k) ?: return null
        s.fish.indexOfFirst { it.col == col && it.row == row }.takeIf { it >= 0 }?.let { return PieceRef(k, Piece.FISH, it) }
        s.golden.indexOfFirst { it.col == col && it.row == row }.takeIf { it >= 0 }?.let { return PieceRef(k, Piece.GOLDEN, it) }
        s.enemies.indexOfFirst { it.col == col && it.row == row }.takeIf { it >= 0 }?.let { return PieceRef(k, Piece.ENEMY, it) }
        s.crabs.indexOfFirst { it.col == col && it.row == row }.takeIf { it >= 0 }?.let { return PieceRef(k, Piece.CRAB, it) }
        return null
    }

    fun remove(level: UserLevel, ref: PieceRef): UserLevel = level.withStage(ref.stage) { s ->
        when (ref.piece) {
            Piece.FISH -> s.copy(fish = s.fish.filterIndexed { i, _ -> i != ref.index })
            Piece.GOLDEN -> s.copy(golden = s.golden.filterIndexed { i, _ -> i != ref.index })
            Piece.ENEMY -> s.copy(enemies = s.enemies.filterIndexed { i, _ -> i != ref.index })
            Piece.CRAB -> s.copy(crabs = s.crabs.filterIndexed { i, _ -> i != ref.index })
        }
    }

    private fun same(level: UserLevel, ref: PieceRef, what: Placement): Boolean {
        val s = level.stages[ref.stage]
        return when (what) {
            is Placement.Fish -> ref.piece == Piece.FISH && s.fish[ref.index].blue == what.blue
            Placement.Golden -> ref.piece == Piece.GOLDEN
            is Placement.Enemy -> ref.piece == Piece.ENEMY && s.enemies[ref.index].kind == what.kind
            Placement.Crab -> ref.piece == Piece.CRAB
        }
    }

    /** Czy etap ma jeszcze miejsce na taką postać. */
    fun hasRoom(stage: UserStage, what: Placement): Boolean = when (what) {
        is Placement.Fish -> stage.fish.size < UserLevels.MAX_FISH
        Placement.Golden -> stage.golden.size < UserLevels.MAX_GOLDEN
        is Placement.Enemy -> stage.enemies.size < UserLevels.MAX_ENEMIES
        Placement.Crab -> stage.crabs.size < UserLevels.MAX_CRABS
    }

    /**
     * Stawia postać na polu (ryby i potwory na wodzie, kraby na piasku). Dotknięcie takiej samej postaci ją usuwa,
     * innej – podmienia. null – na polu nie da się jej postawić albo etap ma już najwięcej takich postaci.
     */
    fun place(level: UserLevel, k: Int, col: Int, row: Int, what: Placement): UserLevel? {
        val stage = level.stages[k]
        val cell = stage.cell(col, row)
        if (what == Placement.Crab && cell != UserLevels.SAND) return null
        if (what != Placement.Crab && cell != UserLevels.SEA) return null
        val existing = pieceAt(level, k, col, row)
        if (existing != null && same(level, existing, what)) return remove(level, existing)
        val base = existing?.let { remove(level, it) } ?: level
        val s = base.stages[k]
        if (!hasRoom(s, what)) return null
        return base.withStage(k) {
            when (what) {
                is Placement.Fish -> it.copy(fish = it.fish + UserFish(what.blue, col, row))
                Placement.Golden -> it.copy(golden = it.golden + UserGolden((3 + it.golden.size * 9).coerceAtMost(UserLevels.MAX_DELAY), col, row))
                is Placement.Enemy -> it.copy(enemies = it.enemies + UserEnemy(what.kind, col, row))
                Placement.Crab -> it.copy(crabs = it.crabs + UserCrab(col, row))
            }
        }
    }

    /** Przenosi postać na inne pole (także do innego etapu); null – tam nie może stać. */
    fun move(level: UserLevel, ref: PieceRef, k: Int, col: Int, row: Int): UserLevel? {
        val target = level.stages.getOrNull(k) ?: return null
        val cell = target.cell(col, row)
        if (ref.piece == Piece.CRAB && cell != UserLevels.SAND) return null
        if (ref.piece != Piece.CRAB && cell != UserLevels.SEA) return null
        if (pieceAt(level, k, col, row) != null) return null
        val s = level.stages[ref.stage]
        val removed = remove(level, ref)
        return removed.withStage(k) {
            when (ref.piece) {
                Piece.FISH -> it.copy(fish = it.fish + s.fish[ref.index].copy(col = col, row = row))
                Piece.GOLDEN -> it.copy(golden = it.golden + s.golden[ref.index].copy(col = col, row = row))
                Piece.ENEMY -> it.copy(enemies = it.enemies + s.enemies[ref.index].copy(col = col, row = row))
                Piece.CRAB -> it.copy(crabs = it.crabs + s.crabs[ref.index].copy(col = col, row = row))
            }
        }.takeIf { hasRoomAfterMove(level, ref, k) }
    }

    private fun hasRoomAfterMove(level: UserLevel, ref: PieceRef, k: Int): Boolean {
        if (k == ref.stage) return true
        val s = level.stages[k]
        return when (ref.piece) {
            Piece.FISH -> s.fish.size < UserLevels.MAX_FISH
            Piece.GOLDEN -> s.golden.size < UserLevels.MAX_GOLDEN
            Piece.ENEMY -> s.enemies.size < UserLevels.MAX_ENEMIES
            Piece.CRAB -> s.crabs.size < UserLevels.MAX_CRABS
        }
    }

    fun setEnemyDir(level: UserLevel, ref: PieceRef, dir: Int): UserLevel = level.withStage(ref.stage) { s ->
        s.copy(enemies = s.enemies.mapIndexed { i, e -> if (i == ref.index) e.copy(dir = UserLevels.validDir(e.kind, dir)) else e })
    }

    fun setGoldenDelay(level: UserLevel, ref: PieceRef, delay: Int): UserLevel = level.withStage(ref.stage) { s ->
        s.copy(golden = s.golden.mapIndexed { i, g -> if (i == ref.index) g.copy(delay = delay.coerceIn(0, UserLevels.MAX_DELAY)) else g })
    }

    fun setFishBlue(level: UserLevel, ref: PieceRef, blue: Boolean): UserLevel = level.withStage(ref.stage) { s ->
        s.copy(fish = s.fish.mapIndexed { i, f -> if (i == ref.index) f.copy(blue = blue) else f })
    }

    fun setDoor(level: UserLevel, k: Int, door: Int): UserLevel = level.withStage(k) { it.copy(door = door.coerceIn(1, WIDTH - 2)) }

    fun setQuota(level: UserLevel, k: Int, quota: Int): UserLevel = level.withStage(k) { it.copy(quota = quota.coerceIn(1, UserLevels.MAX_QUOTA)) }

    fun setStage(level: UserLevel, k: Int, change: (UserStage) -> UserStage): UserLevel = level.withStage(k, change)

    /**
     * Zmienia liczbę rzędów etapu – od góry (pod bramą): nowe rzędy powtarzają brzegi pierwszego rzędu,
     * a przy skracaniu znikają górne rzędy razem z postaciami. Postacie przesuwają się razem z planszą.
     */
    fun setRows(level: UserLevel, k: Int, rows: Int): UserLevel {
        val s = level.stages[k]
        val target = rows.coerceIn(UserLevels.MIN_ROWS, UserLevels.MAX_ROWS)
        val delta = target - s.rows
        if (delta == 0) return level
        val cells = ByteArray(target * WIDTH) { i ->
            val r = i / WIDTH - delta
            val c = i % WIDTH
            if (r >= 0) s.cells[r * WIDTH + c] else s.cells[c].let { if (it == UserLevels.ROCK) UserLevels.SEA else it }
        }
        fun keep(row: Int) = row + delta in 0 until target
        return level.withStage(k) {
            it.copy(
                rows = target,
                cells = cells,
                fish = it.fish.filter { f -> keep(f.row) }.map { f -> f.copy(row = f.row + delta) },
                golden = it.golden.filter { g -> keep(g.row) }.map { g -> g.copy(row = g.row + delta) },
                enemies = it.enemies.filter { e -> keep(e.row) }.map { e -> e.copy(row = e.row + delta) },
                crabs = it.crabs.filter { c -> keep(c.row) }.map { c -> c.copy(row = c.row + delta) },
            )
        }
    }

    /** Nowy pusty etap na miejscu [at] (0 – pierwszy, na dole planszy). */
    fun addStage(level: UserLevel, at: Int): UserLevel? {
        if (level.stages.size >= UserLevels.MAX_STAGES) return null
        val list = level.stages.toMutableList()
        list.add(at.coerceIn(0, list.size), UserLevels.blankStage())
        return level.copy(stages = list)
    }

    /** Kopia etapu tuż nad nim. */
    fun duplicateStage(level: UserLevel, k: Int): UserLevel? {
        if (level.stages.size >= UserLevels.MAX_STAGES) return null
        val list = level.stages.toMutableList()
        list.add(k + 1, list[k].copy(cells = list[k].cells.copyOf()))
        return level.copy(stages = list)
    }

    fun deleteStage(level: UserLevel, k: Int): UserLevel? {
        if (level.stages.size <= 1) return null
        return level.copy(stages = level.stages.filterIndexed { i, _ -> i != k })
    }

    /** Zamienia etap z sąsiednim ([up] – z wyższym, bliżej mety). */
    fun moveStage(level: UserLevel, k: Int, up: Boolean): UserLevel? {
        val other = if (up) k + 1 else k - 1
        if (other !in level.stages.indices) return null
        val list = level.stages.toMutableList()
        list[k] = level.stages[other]
        list[other] = level.stages[k]
        return level.copy(stages = list)
    }

    /**
     * Pola zawsze z wodą (przy wrotach i na starcie – [UserLevels.locked] w [shown]) bierze z [raw]: zmiana
     * zrobiona na poziomie po poprawkach nie gubi piasku, który wróci po przesunięciu etapu w inne miejsce.
     */
    fun keepLocked(edited: UserLevel, shown: UserLevel, raw: UserLevel): UserLevel {
        if (edited.stages.size != shown.stages.size || raw.stages.size != shown.stages.size) return edited
        return edited.copy(
            stages = edited.stages.mapIndexed { k, s ->
                val r = raw.stages[k]
                if (s.rows != r.rows || s.rows != shown.stages[k].rows) return@mapIndexed s
                var cells: ByteArray? = null
                for (row in 0 until s.rows) for (col in 0 until WIDTH) {
                    val i = row * WIDTH + col
                    // Pole z postacią zostaje wodą – inaczej postać zniknęłaby przy poprawkach.
                    if (r.cells[i] != s.cells[i] && UserLevels.locked(shown, k, col, row) && pieceAt(edited, k, col, row) == null) {
                        (cells ?: s.cells.copyOf().also { cells = it })[i] = r.cells[i]
                    }
                }
                cells?.let { s.copy(cells = it) } ?: s
            },
        )
    }

    /** Lustrzane odbicie etapu lewo–prawo (z wrotami, postaciami, kierunkami i prądem). */
    fun mirrorStage(level: UserLevel, k: Int): UserLevel = level.withStage(k) { s ->
        val m = WIDTH - 1
        val cells = ByteArray(s.cells.size) { i -> s.cells[(i / WIDTH) * WIDTH + (m - i % WIDTH)] }
        fun flip(dir: Int) = when (dir) {
            0 -> 2
            2 -> 0
            else -> dir
        }
        s.copy(
            cells = cells,
            door = m - s.door,
            currentX = -s.currentX,
            fish = s.fish.map { it.copy(col = m - it.col) },
            golden = s.golden.map { it.copy(col = m - it.col) },
            enemies = s.enemies.map { it.copy(col = m - it.col, dir = flip(it.dir)) },
            crabs = s.crabs.map { it.copy(col = m - it.col) },
        )
    }

    /** Pusty etap tej samej wysokości: brzegi po bokach, bez postaci. */
    fun clearStage(level: UserLevel, k: Int): UserLevel = level.withStage(k) { s ->
        UserLevels.blankStage(rows = s.rows, door = s.door, quota = 1).copy(theme = s.theme, currentX = s.currentX, currentY = s.currentY)
    }
}
