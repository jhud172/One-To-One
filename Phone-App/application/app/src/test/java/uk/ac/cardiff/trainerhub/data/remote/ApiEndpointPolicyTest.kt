package uk.ac.cardiff.trainerhub.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiEndpointPolicyTest {
    @Test
    fun releaseRequiresHttpsAndRejectsCredentialBearingUrls() {
        assertEquals("https://example.invalid", validatedApiBaseUrl(" https://example.invalid/ ", false))
        for (url in listOf("http://example.invalid", "http://localhost", "https://user:password@example.invalid", "https://example.invalid?token=private", "https://example.invalid#private")) {
            assertThrows(Exception::class.java) { validatedApiBaseUrl(url, false) }
        }
    }

    @Test
    fun debugAllowsExactLocalHostsWithoutAcceptingLookalikeDomains() {
        assertEquals("http://10.0.2.2:8081", validatedApiBaseUrl("http://10.0.2.2:8081/", true))
        assertEquals("http://localhost:8081", validatedApiBaseUrl("http://localhost:8081", true))
        assertThrows(IllegalArgumentException::class.java) { validatedApiBaseUrl("http://localhost.example.invalid", true) }
        assertThrows(IllegalArgumentException::class.java) { validatedApiBaseUrl("http://example.invalid", true) }
    }
}
