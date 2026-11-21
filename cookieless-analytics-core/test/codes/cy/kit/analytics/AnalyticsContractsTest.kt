package codes.cy.kit.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
class AnalyticsContractsTest {

    private val contractJson: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    @Test
    fun `StatsTimeRange serializes wire tokens exposes days and parses inputs cleanly`() {
        assertEquals(7, StatsTimeRange.SEVEN_DAYS.days)
        assertEquals(30, StatsTimeRange.THIRTY_DAYS.days)
        assertEquals(90, StatsTimeRange.NINETY_DAYS.days)
        assertNull(StatsTimeRange.ALL.days)

        assertEquals(StatsTimeRange.THIRTY_DAYS, StatsTimeRange.parse(null))
        assertEquals(StatsTimeRange.THIRTY_DAYS, StatsTimeRange.parse(""))
        assertEquals(StatsTimeRange.THIRTY_DAYS, StatsTimeRange.parse("   "))
        assertEquals(StatsTimeRange.SEVEN_DAYS, StatsTimeRange.parse(" 7D "))
        assertEquals(StatsTimeRange.THIRTY_DAYS, StatsTimeRange.parse("30d"))
        assertEquals(StatsTimeRange.NINETY_DAYS, StatsTimeRange.parse("90d"))
        assertEquals(StatsTimeRange.ALL, StatsTimeRange.parse("ALL"))

        assertFailsWith<IllegalArgumentException> {
            StatsTimeRange.parse("365d")
        }

        val encoded = contractJson.encodeToString(StatsTimeRange.serializer(), StatsTimeRange.SEVEN_DAYS)
        assertEquals("\"7d\"", encoded)
        assertEquals(
            StatsTimeRange.SEVEN_DAYS,
            contractJson.decodeFromString(StatsTimeRange.serializer(), encoded),
        )

        val encodedAll = contractJson.encodeToString(StatsTimeRange.serializer(), StatsTimeRange.ALL)
        assertEquals("\"all\"", encodedAll)
        assertEquals(
            StatsTimeRange.ALL,
            contractJson.decodeFromString(StatsTimeRange.serializer(), encodedAll),
        )
    }

    @Test
    fun `ViewportTier maps adaptive dp thresholds and roundtrips through JSON`() {
        assertEquals(ViewportTier.COMPACT, ViewportTier.fromWidthDp(-1))
        assertEquals(ViewportTier.COMPACT, ViewportTier.fromWidthDp(0))
        assertEquals(ViewportTier.COMPACT, ViewportTier.fromWidthDp(599))
        assertEquals(ViewportTier.MEDIUM, ViewportTier.fromWidthDp(600))
        assertEquals(ViewportTier.MEDIUM, ViewportTier.fromWidthDp(839))
        assertEquals(ViewportTier.EXPANDED, ViewportTier.fromWidthDp(840))
        assertEquals(ViewportTier.EXPANDED, ViewportTier.fromWidthDp(1199))
        assertEquals(ViewportTier.LARGE, ViewportTier.fromWidthDp(1200))
        assertEquals(ViewportTier.LARGE, ViewportTier.fromWidthDp(1599))
        assertEquals(ViewportTier.EXTRA_LARGE, ViewportTier.fromWidthDp(1600))
        assertEquals(ViewportTier.EXTRA_LARGE, ViewportTier.fromWidthDp(2560))

        ViewportTier.entries.forEach { tier ->
            val encoded = contractJson.encodeToString(ViewportTier.serializer(), tier)
            val decoded = contractJson.decodeFromString(ViewportTier.serializer(), encoded)
            assertEquals(tier, decoded)
        }
    }

    @Test
    fun `ClientPlatform roundtrips all entries through JSON`() {
        ClientPlatform.entries.forEach { platform ->
            val encoded = contractJson.encodeToString(ClientPlatform.serializer(), platform)
            val decoded = contractJson.decodeFromString(ClientPlatform.serializer(), encoded)
            assertEquals(platform, decoded)
        }
    }

    @Test
    fun `AnalyticsEventPayload roundtrips minimal and fully populated payloads`() {
        val minimal = AnalyticsEventPayload(
            eventType = "PAGE_VIEW",
            path = "/docs/getting-started",
        )
        val minimalEncoded = contractJson.encodeToString(AnalyticsEventPayload.serializer(), minimal)
        assertTrue(minimalEncoded.contains("\"platform\":\"WEB\""))
        assertTrue(minimalEncoded.contains("\"targetId\":null"))
        assertTrue(minimalEncoded.contains("\"viewportTier\":null"))
        assertTrue(minimalEncoded.contains("\"sessionHint\":null"))
        assertEquals(
            minimal,
            contractJson.decodeFromString(AnalyticsEventPayload.serializer(), minimalEncoded),
        )

        val full = AnalyticsEventPayload(
            eventType = "ASSET_DOWNLOAD",
            path = "/guides/architecture",
            targetId = "10000000-0000-0000-0000-000000000001",
            referrerHost = "example.org",
            platform = ClientPlatform.IOS,
            viewportTier = ViewportTier.COMPACT,
            sessionHint = "tab-ephemeral-1",
        )
        val fullEncoded = contractJson.encodeToString(AnalyticsEventPayload.serializer(), full)
        assertEquals(
            full,
            contractJson.decodeFromString(AnalyticsEventPayload.serializer(), fullEncoded),
        )
    }

    @Test
    fun `AnalyticsSummaryDto and breakdown contracts roundtrip cleanly`() {
        val summary = AnalyticsSummaryDto(
            range = StatsTimeRange.THIRTY_DAYS,
            totalEvents = 1_250L,
            uniqueVisitors = 420L,
            countsByEventType = listOf(
                NamedCountDto(name = "PAGE_VIEW", count = 1_000L),
                NamedCountDto(name = "ASSET_DOWNLOAD", count = 250L),
            ),
            topPaths = listOf(
                PathStatsDto(
                    path = "/docs",
                    eventType = "PAGE_VIEW",
                    eventCount = 600L,
                    uniqueVisitors = 310L,
                ),
            ),
            topReferrers = listOf(NamedCountDto(name = "github.com", count = 180L)),
            byPlatform = listOf(NamedCountDto(name = "WEB", count = 900L)),
            byViewportTier = listOf(NamedCountDto(name = "LARGE", count = 540L)),
            dailyCounts = listOf(
                DailyCountDto(date = "2026-11-20", eventCount = 80L, uniqueVisitors = 45L),
                DailyCountDto(date = "2026-11-21", eventCount = 95L),
            ),
        )

        val encoded = contractJson.encodeToString(AnalyticsSummaryDto.serializer(), summary)
        val decoded = contractJson.decodeFromString(AnalyticsSummaryDto.serializer(), encoded)
        assertEquals(summary, decoded)
        assertEquals(0L, decoded.dailyCounts[1].uniqueVisitors)
    }
}
