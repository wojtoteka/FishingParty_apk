package wojtoteka.ovh.fishingparty.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Achievement
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelSpec
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.Worlds

fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Nazwy światów do użycia także poza kompozycją (np. przy rysowaniu mapy): 40 światów
 * kampanii ma własne nazwy, a światy Bezkresnego Morza składają nazwę z dwóch części.
 */
@Composable
fun rememberWorldNames(): (Int) -> String {
    val campaign = stringArrayResource(R.array.world_names)
    val first = stringArrayResource(R.array.endless_world_a)
    val second = stringArrayResource(R.array.endless_world_b)
    val pattern = stringResource(R.string.endless_world_name)
    val finale = stringResource(R.string.type_finale)
    return { world ->
        when {
            world == Worlds.FINALE -> finale
            !Worlds.isEndless(world) -> campaign[world]
            else -> Worlds.endlessName(world - LevelConstants.WORLD_COUNT).let { (a, b) -> pattern.format(first[a], second[b]) }
        }
    }
}

@Composable
fun typeName(type: LevelType): String = stringResource(
    when (type) {
        LevelType.CLASSIC -> R.string.type_classic
        LevelType.ROCKY -> R.string.type_rocky
        LevelType.ARCHIPELAGO -> R.string.type_archipelago
        LevelType.CANAL -> R.string.type_canal
        LevelType.CURRENT -> R.string.type_current
        LevelType.GOLD_RUSH -> R.string.type_gold_rush
        LevelType.TIME_ATTACK -> R.string.type_time_attack
        LevelType.SWARM -> R.string.type_swarm
        LevelType.BOSS -> R.string.type_boss
        LevelType.FINALE -> R.string.type_finale
    },
)

@Composable
fun typeDescription(type: LevelType): String = stringResource(
    when (type) {
        LevelType.CLASSIC -> R.string.type_classic_desc
        LevelType.ROCKY -> R.string.type_rocky_desc
        LevelType.ARCHIPELAGO -> R.string.type_archipelago_desc
        LevelType.CANAL -> R.string.type_canal_desc
        LevelType.CURRENT -> R.string.type_current_desc
        LevelType.GOLD_RUSH -> R.string.type_gold_rush_desc
        LevelType.TIME_ATTACK -> R.string.type_time_attack_desc
        LevelType.SWARM -> R.string.type_swarm_desc
        LevelType.BOSS -> R.string.type_boss_desc
        LevelType.FINALE -> R.string.type_finale_desc
    },
)

/** Opisy trzech celów gwiazdkowych poziomu. */
@Composable
fun goalTexts(spec: LevelSpec): List<String> = listOf(
    stringResource(R.string.goal_finish),
    when (spec.secondGoal) {
        SecondGoal.NO_DAMAGE -> stringResource(R.string.goal_no_damage)
        SecondGoal.ALL_FISH -> stringResource(R.string.goal_all_fish)
    },
    when (spec.thirdGoal) {
        ThirdGoal.PAR_TIME -> stringResource(R.string.goal_par, formatTime(spec.parSeconds))
        ThirdGoal.SCORE -> stringResource(R.string.goal_score, spec.scoreTarget)
        ThirdGoal.ALL_GOLDEN -> stringResource(R.string.goal_golden)
        ThirdGoal.COMBO -> stringResource(R.string.goal_combo, spec.comboTarget)
    },
)

@Composable
fun powerUpName(p: PowerUp): String = stringResource(
    when (p) {
        PowerUp.MAGNET -> R.string.powerup_magnet
        PowerUp.SHIELD -> R.string.powerup_shield
        PowerUp.TURBO -> R.string.powerup_turbo
        PowerUp.LIFE -> R.string.powerup_life
        PowerUp.NET -> R.string.powerup_net
        PowerUp.FROST -> R.string.powerup_frost
        PowerUp.LURE -> R.string.powerup_lure
        PowerUp.RHYTHM -> R.string.powerup_rhythm
    },
)

@Composable
fun powerUpDescription(p: PowerUp): String = stringResource(
    when (p) {
        PowerUp.MAGNET -> R.string.powerup_magnet_desc
        PowerUp.SHIELD -> R.string.powerup_shield_desc
        PowerUp.TURBO -> R.string.powerup_turbo_desc
        PowerUp.LIFE -> R.string.powerup_life_desc
        PowerUp.NET -> R.string.powerup_net_desc
        PowerUp.FROST -> R.string.powerup_frost_desc
        PowerUp.LURE -> R.string.powerup_lure_desc
        PowerUp.RHYTHM -> R.string.powerup_rhythm_desc
    },
)

fun powerUpIcon(p: PowerUp): String = when (p) {
    PowerUp.MAGNET -> "fish1"
    PowerUp.SHIELD -> "boats/navy/ship"
    PowerUp.TURBO -> "arrowup"
    PowerUp.LIFE -> "boats/classic/ship"
    PowerUp.NET -> "fishblue1"
    PowerUp.FROST -> "sharkfrost1"
    PowerUp.LURE -> "fishgold1"
    PowerUp.RHYTHM -> "combo"
}

/** Duże ikony (rekin, pętla) rysujemy o stopień mniejsze, żeby wszystkie kafelki miały podobną wielkość. */
fun powerUpIconScale(p: PowerUp, base: Int): Int = when (p) {
    PowerUp.FROST, PowerUp.RHYTHM -> (base - 1).coerceAtLeast(1)
    else -> base
}

@Composable
fun itemName(item: ShopItem): String {
    if (item.kind == ItemKind.POWERUP) return powerUpName(PowerUp.entries.first { it.id == item.id })
    return stringResource(
        when (item.key) {
            "boat:classic" -> R.string.boat_classic
            "boat:crimson" -> R.string.boat_crimson
            "boat:navy" -> R.string.boat_navy
            "boat:emerald" -> R.string.boat_emerald
            "boat:ivory" -> R.string.boat_ivory
            "boat:royal" -> R.string.boat_royal
            "boat:shadow" -> R.string.boat_shadow
            "boat:golden" -> R.string.boat_golden
            "boat:sky" -> R.string.boat_sky
            "boat:teal" -> R.string.boat_teal
            "boat:mint" -> R.string.boat_mint
            "boat:lime" -> R.string.boat_lime
            "boat:amber" -> R.string.boat_amber
            "boat:rose" -> R.string.boat_rose
            "boat:lavender" -> R.string.boat_lavender
            "boat:silver" -> R.string.boat_silver
            "sail:white" -> R.string.sail_white
            "sail:red" -> R.string.sail_red
            "sail:blue" -> R.string.sail_blue
            "sail:yellow" -> R.string.sail_yellow
            "sail:green" -> R.string.sail_green
            "sail:black" -> R.string.sail_black
            "sail:striped" -> R.string.sail_striped
            "sail:rainbow" -> R.string.sail_rainbow
            "sail:orange" -> R.string.sail_orange
            "sail:pink" -> R.string.sail_pink
            "sail:purple" -> R.string.sail_purple
            "sail:cyan" -> R.string.sail_cyan
            "sail:marine" -> R.string.sail_marine
            "sail:candy" -> R.string.sail_candy
            "sail:poland" -> R.string.sail_poland
            "sail:pirate" -> R.string.sail_pirate
            "sail:ocean" -> R.string.sail_ocean
            "sail:sunset" -> R.string.sail_sunset
            "sail:gold" -> R.string.sail_gold
            "sailor:ginger" -> R.string.sailor_ginger
            "sailor:blond" -> R.string.sailor_blond
            "sailor:brunet" -> R.string.sailor_brunet
            "sailor:diver" -> R.string.sailor_diver
            "sailor:punk" -> R.string.sailor_punk
            "sailor:captain" -> R.string.sailor_captain
            "sailor:fisher" -> R.string.sailor_fisher
            "sailor:surfer" -> R.string.sailor_surfer
            "sailor:beach" -> R.string.sailor_beach
            "sailor:pirate" -> R.string.sailor_pirate
            "sailor:ninja" -> R.string.sailor_ninja
            "sailor:ghost" -> R.string.sailor_ghost
            "sailor:king" -> R.string.sailor_king
            "sailor:golden" -> R.string.sailor_golden
            else -> R.string.app_name
        },
    )
}

private val ACHIEVEMENT_TEXTS: Map<String, Pair<Int, Int>> = mapOf(
    "first_fish" to (R.string.ach_first_fish to R.string.ach_first_fish_desc),
    "fish_100" to (R.string.ach_fish_100 to R.string.ach_fish_100_desc),
    "fish_1000" to (R.string.ach_fish_1000 to R.string.ach_fish_1000_desc),
    "fish_5000" to (R.string.ach_fish_5000 to R.string.ach_fish_5000_desc),
    "fish_20000" to (R.string.ach_fish_20000 to R.string.ach_fish_20000_desc),
    "golden_1" to (R.string.ach_golden_1 to R.string.ach_golden_1_desc),
    "golden_25" to (R.string.ach_golden_25 to R.string.ach_golden_25_desc),
    "golden_100" to (R.string.ach_golden_100 to R.string.ach_golden_100_desc),
    "level_5" to (R.string.ach_level_5 to R.string.ach_level_5_desc),
    "level_25" to (R.string.ach_level_25 to R.string.ach_level_25_desc),
    "level_50" to (R.string.ach_level_50 to R.string.ach_level_50_desc),
    "level_100" to (R.string.ach_level_100 to R.string.ach_level_100_desc),
    "level_200" to (R.string.ach_level_200 to R.string.ach_level_200_desc),
    "level_350" to (R.string.ach_level_350 to R.string.ach_level_350_desc),
    "level_500" to (R.string.ach_level_500 to R.string.ach_level_500_desc),
    "level_750" to (R.string.ach_level_750 to R.string.ach_level_750_desc),
    "level_1000" to (R.string.ach_level_1000 to R.string.ach_level_1000_desc),
    "stars_15" to (R.string.ach_stars_15 to R.string.ach_stars_15_desc),
    "stars_75" to (R.string.ach_stars_75 to R.string.ach_stars_75_desc),
    "stars_300" to (R.string.ach_stars_300 to R.string.ach_stars_300_desc),
    "stars_750" to (R.string.ach_stars_750 to R.string.ach_stars_750_desc),
    "stars_1500" to (R.string.ach_stars_1500 to R.string.ach_stars_1500_desc),
    "stars_3000" to (R.string.ach_stars_3000 to R.string.ach_stars_3000_desc),
    "perfect_1" to (R.string.ach_perfect_1 to R.string.ach_perfect_1_desc),
    "perfect_25" to (R.string.ach_perfect_25 to R.string.ach_perfect_25_desc),
    "perfect_100" to (R.string.ach_perfect_100 to R.string.ach_perfect_100_desc),
    "nohit_10" to (R.string.ach_nohit_10 to R.string.ach_nohit_10_desc),
    "nohit_50" to (R.string.ach_nohit_50 to R.string.ach_nohit_50_desc),
    "combo_5" to (R.string.ach_combo_5 to R.string.ach_combo_5_desc),
    "boss_1" to (R.string.ach_boss_1 to R.string.ach_boss_1_desc),
    "boss_10" to (R.string.ach_boss_10 to R.string.ach_boss_10_desc),
    "shopper" to (R.string.ach_shopper to R.string.ach_shopper_desc),
    "fashion" to (R.string.ach_fashion to R.string.ach_fashion_desc),
    "fleet" to (R.string.ach_fleet to R.string.ach_fleet_desc),
    "daily_3" to (R.string.ach_daily_3 to R.string.ach_daily_3_desc),
    "daily_7" to (R.string.ach_daily_7 to R.string.ach_daily_7_desc),
    "streak_3" to (R.string.ach_streak_3 to R.string.ach_streak_3_desc),
    "streak_7" to (R.string.ach_streak_7 to R.string.ach_streak_7_desc),
    "streak_30" to (R.string.ach_streak_30 to R.string.ach_streak_30_desc),
    "speed_1" to (R.string.ach_speed_1 to R.string.ach_speed_1_desc),
    "speed_25" to (R.string.ach_speed_25 to R.string.ach_speed_25_desc),
    "rich" to (R.string.ach_rich to R.string.ach_rich_desc),
    "veteran" to (R.string.ach_veteran to R.string.ach_veteran_desc),
)

@Composable
fun achievementTitle(a: Achievement): String = ACHIEVEMENT_TEXTS[a.id]?.let { stringResource(it.first) } ?: a.id

@Composable
fun achievementDescription(a: Achievement): String = ACHIEVEMENT_TEXTS[a.id]?.let { stringResource(it.second) } ?: ""
