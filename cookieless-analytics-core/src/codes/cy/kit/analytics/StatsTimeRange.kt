package codes.cy.kit.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TOKEN_7D: String = "7d"
private const val TOKEN_30D: String = "30d"
private const val TOKEN_90D: String = "90d"
private const val TOKEN_ALL: String = "all"

private const val DAYS_SEVEN: Int = 7
private const val DAYS_THIRTY: Int = 30
private const val DAYS_NINETY: Int = 90

/**
 * Strongly typed aggregation window for analytics queries, serialized as lowercase wire tokens.
 *
 * @property wireValue Canonical query-parameter and JSON representation (`7d`, `30d`, `90d`, `all`).
 * @property days Number of trailing days covered by this window, or `null` for [ALL].
 */
@Serializable
public enum class StatsTimeRange(
    public val wireValue: String,
    public val days: Int?,
) {
    /** Trailing 7-day window (`7d`). */
    @SerialName(TOKEN_7D)
    SEVEN_DAYS(TOKEN_7D, DAYS_SEVEN),

    /** Trailing 30-day window (`30d`). */
    @SerialName(TOKEN_30D)
    THIRTY_DAYS(TOKEN_30D, DAYS_THIRTY),

    /** Trailing 90-day window (`90d`). */
    @SerialName(TOKEN_90D)
    NINETY_DAYS(TOKEN_90D, DAYS_NINETY),

    /** Unbounded all-time aggregation window (`all`). */
    @SerialName(TOKEN_ALL)
    ALL(TOKEN_ALL, null),
    ;

    /**
     * Wire token constants and pure parsing helpers for [StatsTimeRange].
     */
    public companion object {
        /** Canonical wire representation for [SEVEN_DAYS]. */
        public const val WIRE_7D: String = TOKEN_7D

        /** Canonical wire representation for [THIRTY_DAYS]. */
        public const val WIRE_30D: String = TOKEN_30D

        /** Canonical wire representation for [NINETY_DAYS]. */
        public const val WIRE_90D: String = TOKEN_90D

        /** Canonical wire representation for [ALL]. */
        public const val WIRE_ALL: String = TOKEN_ALL

        /**
         * Pure query parsing an optional query parameter into a [StatsTimeRange].
         * Blank or `null` values default to [THIRTY_DAYS]; unrecognized values throw [IllegalArgumentException].
         */
        public fun parse(raw: String?): StatsTimeRange {
            val normalized = raw?.trim()?.lowercase()
            if (normalized.isNullOrEmpty()) {
                return THIRTY_DAYS
            }
            return entries.firstOrNull { entry -> entry.wireValue == normalized }
                ?: throw IllegalArgumentException(
                    "Invalid stats range '$raw'. Expected one of: ${
                        entries.joinToString(separator = ", ") { it.wireValue }
                    }.",
                )
        }
    }
}
