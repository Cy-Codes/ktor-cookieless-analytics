package codes.cy.kit.analytics.server

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VisitorHashGeneratorTest {

    @Test
    fun `computeVisitorHash produces deterministic 64-char lowercase SHA-256 hex matching reference vector`() {
        val date = LocalDate.of(2026, 11, 21)
        val first = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )
        val second = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )

        assertEquals(VisitorHashGenerator.HASH_HEX_LENGTH, first.length)
        assertTrue(HEX_64_REGEX.matches(first))
        assertEquals(first, second)
        assertEquals(EXPECTED_VECTOR_HASH, first)
    }

    @Test
    fun `computeVisitorHash trims surrounding whitespace from salt clientIp and userAgent`() {
        val date = LocalDate.of(2026, 11, 21)
        val clean = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )
        val padded = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "  secret-salt\t",
            clientIp = "\n 203.0.113.42 ",
            userAgent = "  Mozilla/5.0  ",
        )

        assertEquals(clean, padded)
    }

    @Test
    fun `computeVisitorHash rotates fingerprint daily when dateUtc changes`() {
        val dayOne = VisitorHashGenerator.computeVisitorHash(
            dateUtc = LocalDate.of(2026, 11, 21),
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )
        val dayTwo = VisitorHashGenerator.computeVisitorHash(
            dateUtc = LocalDate.of(2026, 11, 22),
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )

        assertNotEquals(dayOne, dayTwo)
        assertTrue(HEX_64_REGEX.matches(dayTwo))
    }

    @Test
    fun `computeVisitorHash is sensitive to salt clientIp userAgent and field boundaries`() {
        val date = LocalDate.of(2026, 11, 21)
        val baseline = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "salt-a",
            clientIp = "198.51.100.10",
            userAgent = "Agent/1.0",
        )
        val differentSalt = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "salt-b",
            clientIp = "198.51.100.10",
            userAgent = "Agent/1.0",
        )
        val differentIp = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "salt-a",
            clientIp = "198.51.100.11",
            userAgent = "Agent/1.0",
        )
        val differentUa = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "salt-a",
            clientIp = "198.51.100.10",
            userAgent = "Agent/2.0",
        )
        val shiftedBoundary = VisitorHashGenerator.computeVisitorHash(
            dateUtc = date,
            salt = "salt-a|198.51.100.10",
            clientIp = "Agent",
            userAgent = "1.0",
        )

        assertNotEquals(baseline, differentSalt)
        assertNotEquals(baseline, differentIp)
        assertNotEquals(baseline, differentUa)
        assertNotEquals(baseline, shiftedBoundary)
    }

    @Test
    fun `computeVisitorHash with Clock resolves UTC date regardless of Clock zone`() {
        // 2026-11-21T23:30:00-05:00 equals 2026-11-22T04:30:00Z in UTC.
        val instantAfterUtcMidnight = Instant.parse("2026-11-22T04:30:00Z")
        val newYorkClock = Clock.fixed(instantAfterUtcMidnight, ZoneId.of("America/New_York"))
        val utcClock = Clock.fixed(instantAfterUtcMidnight, ZoneOffset.UTC)

        val fromNewYorkClock = VisitorHashGenerator.computeVisitorHash(
            clock = newYorkClock,
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )
        val fromUtcClock = VisitorHashGenerator.computeVisitorHash(
            clock = utcClock,
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )
        val explicitUtcDate = VisitorHashGenerator.computeVisitorHash(
            dateUtc = LocalDate.of(2026, 11, 22),
            salt = "secret-salt",
            clientIp = "203.0.113.42",
            userAgent = "Mozilla/5.0",
        )

        assertEquals(explicitUtcDate, fromUtcClock)
        assertEquals(explicitUtcDate, fromNewYorkClock)
    }

    private companion object {
        val HEX_64_REGEX: Regex = Regex("^[0-9a-f]{64}$")
        const val EXPECTED_VECTOR_HASH: String =
            "aa09bb699d45cc9360841dfe11183d4cac27a63775400ab139f0f430d0337cff"
    }
}
