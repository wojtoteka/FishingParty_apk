package wojtoteka.ovh.fishingparty

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import wojtoteka.ovh.fishingparty.level.UserLevelRating
import wojtoteka.ovh.fishingparty.level.UserLevels
import java.io.File

/**
 * Próbka poziomów z kreatora dla serwera rankingu (`test/fixtures/user-level-sample.json`):
 * zapis poziomu i to, co liczy z niego gra – limity wyniku (te same kolumny co level-limits.json),
 * trudność, stopień i problemy. Serwer (src/userlevels.ts) musi policzyć z zapisu to samo.
 */
class UserLevelExportTest {

    @Test
    fun exportUserLevelSample() {
        val levels = JSONArray()
        for (seed in 1L..300L) {
            val level = UserLevelSamples.random(seed * 7919L)
            val built = UserLevels.build(level)
            val plan = built.plan
            val estimate = UserLevelRating.estimate(level)
            levels.put(
                JSONObject()
                    .put("data", UserLevels.encode(level))
                    .put("maxScore", LevelGenerator.maxScore(plan))
                    .put("minMs", (ScoreValidator.minimumSeconds(plan) * 1000f).toLong())
                    .put("common", plan.stages.sumOf { it.common })
                    .put("blue", plan.stages.sumOf { it.blue })
                    .put("golden", plan.goldenCount)
                    .put("stages", plan.stages.size)
                    .put("parSeconds", plan.spec.parSeconds)
                    .put("difficulty", estimate.difficulty)
                    .put("tier", estimate.tier)
                    .put("problems", JSONArray(UserLevels.check(level).map { it.kind.name.lowercase() }.distinct().sorted())),
            )
        }
        val out = File("build/user-level-sample.json")
        out.parentFile?.mkdirs()
        out.writeText(JSONObject().put("levels", levels).toString(1) + "\n")
        assertTrue(out.length() > 0)
    }
}
