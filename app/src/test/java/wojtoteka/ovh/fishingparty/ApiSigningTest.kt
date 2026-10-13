package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import wojtoteka.ovh.fishingparty.online.ApiSigning

/** Podpis musi być identyczny jak w serwerze Top_FishingParty (test/anticheat.test.ts, ten sam wektor). */
class ApiSigningTest {

    @Test
    fun signatureMatchesServer() {
        val bodyHash = ApiSigning.sha256Hex("{\"a\":1}".toByteArray())
        assertEquals("015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862", bodyHash)
        val message = ApiSigning.message("post", "/api/v1/runs", 1_800_000_000_000L, "0123456789abcdef0123456789abcdef", bodyHash)
        assertEquals("POST\n/api/v1/runs\n1800000000000\n0123456789abcdef0123456789abcdef\n$bodyHash", message)
        assertEquals("b3b25ed22c1f11e0264f1b9fb82f804d42a11da667bcb7f4eac9115992d831e8", ApiSigning.sign("test-secret", message))
    }

    @Test
    fun noncesAreRandomHex() {
        val a = ApiSigning.nonce()
        assertEquals(32, a.length)
        assert(a.all { it in "0123456789abcdef" })
        assertNotEquals(a, ApiSigning.nonce())
    }
}
