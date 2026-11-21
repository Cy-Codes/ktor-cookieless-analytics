package codes.cy.kit.analytics.server

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pure daily-salted SHA-256 visitor fingerprint generator storing zero raw IPs or cookies.
 *
 * Computes an irreversible 64-character lowercase hexadecimal digest from
 * `"$dateUtc|${salt.trim()}|${clientIp.trim()}|${userAgent.trim()}"`, rotating daily at UTC
 * midnight without persisting client IP addresses or browser cookies.
 */
public object VisitorHashGenerator {
    /** Digest algorithm used for visitor fingerprint derivation. */
    public const val HASH_ALGORITHM: String = "SHA-256"

    /** Fixed lowercase hexadecimal length produced by SHA-256 digests. */
    public const val HASH_HEX_LENGTH: Int = 64

    private const val HEX_RADIX: Int = 16
    private const val HEX_BYTE_WIDTH: Int = 2
    private const val HEX_PAD_CHAR: Char = '0'
    private const val FIELD_SEPARATOR: String = "|"

    /**
     * Pure query computing a deterministic daily-salted SHA-256 visitor fingerprint for [dateUtc].
     *
     * @param dateUtc UTC calendar date providing daily fingerprint rotation.
     * @param salt Server-side secret salt (trimmed before hashing).
     * @param clientIp Request client IP address (trimmed before hashing; never stored).
     * @param userAgent Request User-Agent header value (trimmed before hashing; never stored).
     * @return Lowercase 64-character SHA-256 hexadecimal fingerprint.
     */
    public fun computeVisitorHash(
        dateUtc: LocalDate,
        salt: String,
        clientIp: String,
        userAgent: String,
    ): String {
        val normalizedInput = listOf(
            dateUtc.toString(),
            salt.trim(),
            clientIp.trim(),
            userAgent.trim(),
        ).joinToString(separator = FIELD_SEPARATOR)

        val digestBytes = MessageDigest.getInstance(HASH_ALGORITHM)
            .digest(normalizedInput.toByteArray(StandardCharsets.UTF_8))

        return digestBytes.joinToString(separator = "") { byte ->
            byte.toUByte().toString(HEX_RADIX).padStart(HEX_BYTE_WIDTH, HEX_PAD_CHAR)
        }
    }

    /**
     * Pure query deriving the UTC calendar date from [clock] and computing the daily-salted
     * SHA-256 visitor fingerprint.
     *
     * @param clock Clock source used to determine the current UTC date.
     * @param salt Server-side secret salt (trimmed before hashing).
     * @param clientIp Request client IP address (trimmed before hashing; never stored).
     * @param userAgent Request User-Agent header value (trimmed before hashing; never stored).
     * @return Lowercase 64-character SHA-256 hexadecimal fingerprint.
     */
    public fun computeVisitorHash(
        clock: Clock,
        salt: String,
        clientIp: String,
        userAgent: String,
    ): String = computeVisitorHash(
        dateUtc = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate(),
        salt = salt,
        clientIp = clientIp,
        userAgent = userAgent,
    )
}
