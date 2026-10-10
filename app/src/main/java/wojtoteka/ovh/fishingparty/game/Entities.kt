package wojtoteka.ovh.fishingparty.game

import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.FishKind

enum class Sfx { FISH, FISH_GOLD, COMBO, HIT, LOST, START, BUMP, GATE_OPEN, STAGE, WIN, CLICK, COIN, ACHIEVEMENT, SHIELD, STAR }

/** Wzmocnienia wybrane przed startem poziomu. */
class Loadout(
    val magnet: Boolean = false,
    val shield: Boolean = false,
    val turbo: Boolean = false,
    val extraLife: Boolean = false,
)

class Fish(
    var x: Float,
    var y: Float,
    val kind: FishKind,
    val stage: Int,
    var facing: Int,
    var vertical: Int,
    val phase: Float,
) {
    /** Czas życia złotej ryby (ucieka po jego upływie). */
    var life = 0f
}

/** Złowiona ryba lecąca do łodzi (jak w oryginale). */
class CaughtFish(var x: Float, var y: Float, var timer: Float)

class Enemy(
    val kind: EnemyKind,
    var x: Float,
    var y: Float,
    val stage: Int,
    var dirX: Int,
    var dirY: Int,
    val phase: Float,
) {
    var dash = 0f
    var fadeIn = 1.2f
    val harmless: Boolean get() = fadeIn > 0f
}

class Popup(var x: Float, var y: Float, val value: Int, val golden: Boolean) {
    var age = 0f
}

enum class MessageKind { GO, FINALE, STAGE, GATE_OPEN, FINISH_OPEN, FINISH, LAST_LIFE, SHIELD, TIME_LOW, COMBO }

class GameMessage(val kind: MessageKind, val arg: Int, val arg2: Int) {
    var age = 0f
}

enum class Phase { PLAYING, WINNING, LOSING, WON, LOST }

enum class LoseReason { CAUGHT, TIME }
