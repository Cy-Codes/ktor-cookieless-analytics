package codes.cy.kit.analytics

import kotlinx.serialization.Serializable

/**
 * Five-tier responsive viewport width classification aligned with Material 3 adaptive breakpoints.
 */
@Serializable
public enum class ViewportTier {
    /** Window width strictly below [BREAKPOINT_MEDIUM_DP] dp. */
    COMPACT,

    /** Window width from [BREAKPOINT_MEDIUM_DP] dp up to [BREAKPOINT_EXPANDED_DP] dp. */
    MEDIUM,

    /** Window width from [BREAKPOINT_EXPANDED_DP] dp up to [BREAKPOINT_LARGE_DP] dp. */
    EXPANDED,

    /** Window width from [BREAKPOINT_LARGE_DP] dp up to [BREAKPOINT_EXTRA_LARGE_DP] dp. */
    LARGE,

    /** Window width at or above [BREAKPOINT_EXTRA_LARGE_DP] dp. */
    EXTRA_LARGE,
    ;

    /**
     * Material 3 adaptive width thresholds and pure lookup helpers for [ViewportTier].
     */
    public companion object {
        /** Lower width bound in dp for [MEDIUM]. */
        public const val BREAKPOINT_MEDIUM_DP: Int = 600

        /** Lower width bound in dp for [EXPANDED]. */
        public const val BREAKPOINT_EXPANDED_DP: Int = 840

        /** Lower width bound in dp for [LARGE]. */
        public const val BREAKPOINT_LARGE_DP: Int = 1200

        /** Lower width bound in dp for [EXTRA_LARGE]. */
        public const val BREAKPOINT_EXTRA_LARGE_DP: Int = 1600

        /**
         * Pure query mapping a window width in density-independent pixels ([widthDp])
         * to its matching [ViewportTier].
         */
        public fun fromWidthDp(widthDp: Int): ViewportTier =
            when {
                widthDp >= BREAKPOINT_EXTRA_LARGE_DP -> EXTRA_LARGE
                widthDp >= BREAKPOINT_LARGE_DP -> LARGE
                widthDp >= BREAKPOINT_EXPANDED_DP -> EXPANDED
                widthDp >= BREAKPOINT_MEDIUM_DP -> MEDIUM
                else -> COMPACT
            }
    }
}
