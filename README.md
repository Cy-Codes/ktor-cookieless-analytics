# ktor-cookieless-analytics

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

Privacy-first, cookieless telemetry contracts, non-blocking FlowMVI intent instrumentation, and daily-salted SHA-256 visitor fingerprinting for Kotlin Multiplatform clients and Ktor JVM backends. Zero raw IP addresses, zero browser cookies, and zero domain-specific assumptions.

Repository: <https://github.com/cy-codes/ktor-cookieless-analytics>

---

## Maven Coordinates

All artifacts share group `codes.cy.kit` and version `0.1.0` with `kotlin.explicitApi: strict` enabled:

| Module | Coordinates | Targets | Purpose |
| :--- | :--- | :--- | :--- |
| **`cookieless-analytics-core`** | `codes.cy.kit:cookieless-analytics-core:0.1.0` | `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs` | Serializable telemetry contracts (`AnalyticsEventPayload`, `StatsTimeRange`, `ClientPlatform`, `ViewportTier`, summary DTOs), `AnalyticsEventSink`, and non-blocking `installAnalyticsPlugin` |
| **`cookieless-analytics-server`** | `codes.cy.kit:cookieless-analytics-server:0.1.0` | `jvm` | Exports `cookieless-analytics-core` and provides daily-salted SHA-256 `VisitorHashGenerator` |

### Consuming via Amper (`libs.versions.toml` + `module.yaml`)

```toml
[libraries]
cookieless-analytics-core = { module = "codes.cy.kit:cookieless-analytics-core", version = "0.1.0" }
cookieless-analytics-server = { module = "codes.cy.kit:cookieless-analytics-server", version = "0.1.0" }
```

```yaml
repositories:
  - id: mavenLocal
    url: mavenLocal
    resolve: true

dependencies:
  - $libs.cookieless.analytics.core # in KMP presentation/shared modules
  # or
  - $libs.cookieless.analytics.server # in Ktor JVM server modules (re-exports core)
```

---

## Public API Overview

### 1. `cookieless-analytics-core` (`codes.cy.kit.analytics`)

- **`AnalyticsEventPayload`**: Immutable `@Serializable` event payload carrying consumer-defined `eventType: String`, `path: String`, optional `targetId: String?`, `referrerHost: String?`, `platform: ClientPlatform = ClientPlatform.WEB`, `viewportTier: ViewportTier? = null`, and `sessionHint: String? = null`.
- **`StatsTimeRange`**: Aggregation window enum (`SEVEN_DAYS` / `"7d"`, `THIRTY_DAYS` / `"30d"`, `NINETY_DAYS` / `"90d"`, `ALL` / `"all"`) exposing `wireValue: String`, `days: Int?`, and pure `StatsTimeRange.parse(raw: String?)`.
- **`ClientPlatform`**: Runtime family enum (`WEB`, `ANDROID`, `IOS`, `DESKTOP`).
- **`ViewportTier`**: Five-tier Material 3 responsive breakpoint enum (`COMPACT`, `MEDIUM`, `EXPANDED`, `LARGE`, `EXTRA_LARGE`) with `BREAKPOINT_*_DP` constants and pure `ViewportTier.fromWidthDp(widthDp: Int)`.
- **Summary & Rollup DTOs**: `NamedCountDto`, `PathStatsDto`, `DailyCountDto`, and `AnalyticsSummaryDto` for domain-agnostic analytics read models.
- **`AnalyticsEventSink<in E>`**: Side-effecting `fun interface` (`suspend fun recordEvent(event: E)`) isolating telemetry writes from read queries per CQS.
- **`StoreBuilder<S, I, A>.installAnalyticsPlugin(...)`**: Installs `"CookielessAnalyticsPlugin"` (`COOKIELESS_ANALYTICS_PLUGIN_NAME`) on FlowMVI stores, mapping intents into optional events (`mapIntentToEvent`) and dispatching via non-blocking `launch` while swallowing non-cancellation exceptions (preserving `CancellationException` propagation). Overloads support both `suspend (E) -> Unit` lambdas and `AnalyticsEventSink<E>`, with an optional `platform` + `viewportTierProvider: () -> ViewportTier` injection variant.

### 2. `cookieless-analytics-server` (`codes.cy.kit.analytics.server`)

- **`VisitorHashGenerator`**: Stateless pure SHA-256 fingerprint utility (`HASH_ALGORITHM = "SHA-256"`, `HASH_HEX_LENGTH = 64`):
  - `computeVisitorHash(dateUtc: LocalDate, salt: String, clientIp: String, userAgent: String): String`
  - `computeVisitorHash(clock: Clock, salt: String, clientIp: String, userAgent: String): String`
  - Normalizes inputs as `"$dateUtc|${salt.trim()}|${clientIp.trim()}|${userAgent.trim()}"` and outputs a lowercase 64-character hex digest so fingerprints rotate daily at UTC midnight without storing raw IPs or cookies.

---

## Usage Snippets

### KMP Presentation Store (FlowMVI)

```kotlin
import codes.cy.kit.analytics.AnalyticsEventPayload
import codes.cy.kit.analytics.AnalyticsEventSink
import codes.cy.kit.analytics.ClientPlatform
import codes.cy.kit.analytics.ViewportTier
import codes.cy.kit.analytics.installAnalyticsPlugin
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce

fun createArticleStore(
    analyticsSink: AnalyticsEventSink<AnalyticsEventPayload>,
    platform: ClientPlatform,
    viewportTierProvider: () -> ViewportTier,
) = store<ArticleState, ArticleIntent, ArticleAction>(initial = ArticleState()) {
    installAnalyticsPlugin(
        analyticsSink = analyticsSink,
        platform = platform,
        viewportTierProvider = viewportTierProvider,
    ) { intent, clientPlatform, tier ->
        when (intent) {
            is ArticleIntent.Open -> AnalyticsEventPayload(
                eventType = "PAGE_VIEW",
                path = "/articles/${intent.slug}",
                platform = clientPlatform,
                viewportTier = tier,
            )
            ArticleIntent.Scroll -> null
        }
    }
    reduce { intent ->
        // Intent reduction never waits on telemetry dispatch.
    }
}
```

### Ktor JVM Ingestion Handler

```kotlin
import codes.cy.kit.analytics.AnalyticsEventPayload
import codes.cy.kit.analytics.server.VisitorHashGenerator
import java.time.Clock

suspend fun recordTelemetry(
    payload: AnalyticsEventPayload,
    clientIp: String,
    userAgent: String,
    dailySalt: String,
    clock: Clock = Clock.systemUTC(),
    insertEvent: suspend (AnalyticsEventPayload, String) -> Unit,
) {
    val visitorHash = VisitorHashGenerator.computeVisitorHash(
        clock = clock,
        salt = dailySalt,
        clientIp = clientIp,
        userAgent = userAgent,
    )
    insertEvent(payload, visitorHash)
}
```

### Pairing with Template PostgreSQL Schema (`analytics.events` & `analytics.daily_rollups`)

This library pairs with the starter template's `analytics` PostgreSQL schema (and keeps `GET` endpoints 100% read-only by ingesting telemetry via `POST`):

```sql
CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE analytics.events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_type TEXT NOT NULL,
    path TEXT NOT NULL,
    target_id TEXT NULL,
    referrer_host TEXT NULL,
    platform TEXT NOT NULL CHECK (platform IN ('WEB', 'ANDROID', 'IOS', 'DESKTOP')),
    viewport_tier TEXT NULL CHECK (viewport_tier IN ('COMPACT', 'MEDIUM', 'EXPANDED', 'LARGE', 'EXTRA_LARGE')),
    visitor_hash TEXT NOT NULL CHECK (char_length(visitor_hash) = 64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE analytics.daily_rollups (
    day DATE NOT NULL,
    event_type TEXT NOT NULL,
    path TEXT NOT NULL,
    platform TEXT NOT NULL CHECK (platform IN ('WEB', 'ANDROID', 'IOS', 'DESKTOP')),
    viewport_tier TEXT NOT NULL CHECK (viewport_tier IN ('COMPACT', 'MEDIUM', 'EXPANDED', 'LARGE', 'EXTRA_LARGE')),
    event_count BIGINT NOT NULL DEFAULT 0 CHECK (event_count >= 0),
    unique_visitors BIGINT NOT NULL DEFAULT 0 CHECK (unique_visitors >= 0),
    PRIMARY KEY (day, event_type, path, platform, viewport_tier)
);
```

When writing from a least-privilege public DB role granted `INSERT`-only access on `analytics.events`, execute inserts with `Statement.NO_GENERATED_KEYS` so PgJDBC does not emit an implicit `RETURNING *` requiring `SELECT` permission.

---

## Build, Test & Publish

This repository uses Kotlin Toolchain (`0.13.0` / `./kotlin`):

```bash
# Compile all targets (jvm, android, iosArm64, iosSimulatorArm64, wasmJs)
./kotlin build

# Run unit tests across JVM, Android, iOS Simulator, and WasmJS
./kotlin test

# Publish both modules (with sources and javadocs) to ~/.m2/repository
./kotlin publish mavenLocal
```

### Verification Summary

- `./kotlin build`: `Build successful` across all 46 tasks (`cookieless-analytics-core` on `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`; `cookieless-analytics-server` on `jvm`).
- `./kotlin test`: `Tests: 49 passed` (0 failed):
  - `cookieless-analytics-core`: 11 tests × 4 platforms (`jvm`, `android`, `iosSimulatorArm64`, `wasmJs`) = 44 passed (`AnalyticsContractsTest`: 5, `AnalyticsPluginTest`: 6).
  - `cookieless-analytics-server`: 5 JVM tests = 5 passed (`VisitorHashGeneratorTest`: 5).
- `./kotlin publish mavenLocal`: `Publication successful` — artifacts published for `codes.cy.kit:cookieless-analytics-core:0.1.0` (common + `-jvm`, `-android`, `-iosarm64`, `-iossimulatorarm64`, `-wasmjs`) and `codes.cy.kit:cookieless-analytics-server:0.1.0` under `~/.m2/repository/codes/cy/kit/`.
