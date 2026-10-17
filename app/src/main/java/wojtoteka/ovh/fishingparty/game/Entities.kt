package wojtoteka.ovh.fishingparty.game

import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.FishKind

enum class Sfx { FISH, FISH_GOLD, COMBO, HIT, LOST, START, BUMP, GATE_OPEN, STAGE, WIN, CLICK, COIN, ACHIEVEMENT, SHIELD, STAR }

/**
 * Wzmocnienia wybrane przed startem poziomu. Żadne nie podnosi maksymalnego wyniku
 * ani nie skraca minimalnego czasu przejazdu – limity anty-cheat zostają bez zmian.
 */
class Loadout(
    val magnet: Boolean = false,
    val shield: Boolean = false,
    val turbo: Boolean = false,
    val extraLife: Boolean = false,
    /** Sieć: ryby łowią się z większej odległości. */
    val net: Boolean = false,
    /** Lodowa mgła: potwory płyną wolniej. */
    val frost: Boolean = false,
    /** Złota przynęta: złote ryby pojawiają się szybciej, zostają dłużej i mniej uciekają. */
    val lure: Boolean = false,
    /** Rytm: dłuższy czas na podtrzymanie combo. */
    val rhythm: Boolean = false,
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

    // Widmokuda: pływa w dowolnym kierunku i poluje na to, co widzi.
    var hunt = Hunt.PATROL
    var huntTimer = 0f
    var vx = 0f
    var vy = 0f

    /** W którą stronę patrzy (pływając w pionie, zostaje przy ostatniej). */
    var faceRight = true

    /** Gdzie ostatnio widziała łódź. */
    var seenX = 0f
    var seenY = 0f

    /** Przerwa po ataku: pływa spokojnie jak ryba i nie poluje, dopóki nie minie. */
    var calm = 0f

    /** Namierzyła łódź (oczy świecą na czerwono, nad głową wykrzyknik). */
    val locked: Boolean get() = hunt == Hunt.AIM || hunt == Hunt.DASH
}

/** Kolejne fazy polowania widmokudy. */
enum class Hunt {
    /** Pływa i rozgląda się przed siebie. */
    PATROL,

    /** Zobaczyła łódź: staje, obraca się do niej i za chwilę skoczy (czas na unik). */
    AIM,

    /** Błyskawiczny skok w miejsce, gdzie widziała łódź. */
    DASH,

    /** Po skoku (trafionym czy nie) odpływa od łodzi, a potem ma przerwę ([Enemy.calm]). */
    FLEE,
}

class Popup(var x: Float, var y: Float, val value: Int, val golden: Boolean) {
    var age = 0f
}

enum class MessageKind { GO, FINALE, STAGE, GATE_OPEN, FINISH_OPEN, FINISH, LAST_LIFE, SHIELD, TIME_LOW, COMBO, SPOTTED }

class GameMessage(val kind: MessageKind, val arg: Int, val arg2: Int) {
    var age = 0f
}

enum class Phase { PLAYING, WINNING, LOSING, WON, LOST }

enum class LoseReason { CAUGHT, TIME }
