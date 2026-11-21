package codes.cy.kit.analytics

import kotlinx.serialization.Serializable

private const val DEFAULT_UNIQUE_VISITORS: Long = 0L

/**
 * Domain-agnostic cookieless telemetry event payload sent from clients to ingestion endpoints.
 *
 * Consumers define their own [eventType] discriminators (for example `"PAGE_VIEW"`,
 * `"DOCUMENT_DOWNLOAD"`, or `"SEARCH_QUERY"`) without coupling to a fixed domain enum.
 *
 * @property eventType Consumer-defined event type identifier.
 * @property path Canonical route or resource path associated with the event (e.g. `"/docs/guide"`).
 * @property targetId Optional opaque target identifier associated with the event.
 * @property referrerHost Optional normalized referrer hostname when available.
 * @property platform Client runtime family emitting the event; defaults to [ClientPlatform.WEB].
 * @property viewportTier Optional responsive viewport tier active at event time.
 * @property sessionHint Optional opaque, ephemeral client-side correlation hint (non-persistent).
 */
@Serializable
public data class AnalyticsEventPayload(
    public val eventType: String,
    public val path: String,
    public val targetId: String? = null,
    public val referrerHost: String? = null,
    public val platform: ClientPlatform = ClientPlatform.WEB,
    public val viewportTier: ViewportTier? = null,
    public val sessionHint: String? = null,
)

/**
 * Generic label-and-count breakdown item used across analytics summaries.
 *
 * @property name Dimension value label (for example platform, viewport tier, or referrer host).
 * @property count Total count recorded for [name].
 */
@Serializable
public data class NamedCountDto(
    public val name: String,
    public val count: Long,
)

/**
 * Aggregated analytics metrics for a specific [path] and [eventType].
 *
 * @property path Recorded route or resource path.
 * @property eventType Consumer-defined event type identifier.
 * @property eventCount Total matching event occurrences.
 * @property uniqueVisitors Approximate distinct daily-salted visitor count.
 */
@Serializable
public data class PathStatsDto(
    public val path: String,
    public val eventType: String,
    public val eventCount: Long,
    public val uniqueVisitors: Long,
)

/**
 * Daily time-series bucket pairing an ISO-8601 UTC date string with event and visitor counts.
 *
 * @property date ISO-8601 UTC calendar date (`YYYY-MM-DD`).
 * @property eventCount Total events recorded on [date].
 * @property uniqueVisitors Distinct daily-salted visitors observed on [date].
 */
@Serializable
public data class DailyCountDto(
    public val date: String,
    public val eventCount: Long,
    public val uniqueVisitors: Long = DEFAULT_UNIQUE_VISITORS,
)

/**
 * Domain-agnostic analytics summary across a requested [StatsTimeRange].
 *
 * @property range Aggregation window represented by this summary.
 * @property totalEvents Total events recorded within [range].
 * @property uniqueVisitors Distinct daily-salted visitors counted across [range].
 * @property countsByEventType Event count breakdown grouped by consumer event type.
 * @property topPaths Highest-traffic paths grouped by path and event type.
 * @property topReferrers Highest-traffic referrer host counts.
 * @property byPlatform Breakdown grouped by [ClientPlatform].
 * @property byViewportTier Breakdown grouped by [ViewportTier].
 * @property dailyCounts Ordered daily rollup buckets within [range].
 */
@Serializable
public data class AnalyticsSummaryDto(
    public val range: StatsTimeRange,
    public val totalEvents: Long,
    public val uniqueVisitors: Long,
    public val countsByEventType: List<NamedCountDto> = emptyList(),
    public val topPaths: List<PathStatsDto> = emptyList(),
    public val topReferrers: List<NamedCountDto> = emptyList(),
    public val byPlatform: List<NamedCountDto> = emptyList(),
    public val byViewportTier: List<NamedCountDto> = emptyList(),
    public val dailyCounts: List<DailyCountDto> = emptyList(),
)
