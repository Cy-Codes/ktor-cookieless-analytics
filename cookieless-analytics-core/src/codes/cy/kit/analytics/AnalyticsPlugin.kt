package codes.cy.kit.analytics

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.dsl.StoreBuilder
import pro.respawn.flowmvi.dsl.plugin

/**
 * Default FlowMVI plugin name registered by [installAnalyticsPlugin].
 */
public const val COOKIELESS_ANALYTICS_PLUGIN_NAME: String = "CookielessAnalyticsPlugin"

/**
 * Side-effecting telemetry sink isolating analytics event dispatch from read queries per CQS.
 *
 * @param E Analytics event type dispatched by the store.
 */
public fun interface AnalyticsEventSink<in E> {
    /**
     * Side effect: dispatches a single analytics [event] asynchronously to a collector or backend.
     */
    public suspend fun recordEvent(event: E)
}

/**
 * Side effect: installs a non-blocking FlowMVI plugin that maps incoming intents via
 * [mapIntentToEvent] and dispatches non-null events through [sink] inside a child coroutine.
 *
 * Non-cancellation exceptions thrown by [sink] are swallowed so telemetry failures never block or
 * crash the store; [CancellationException] propagates per structured concurrency.
 *
 * @param sink Suspending function receiving each mapped telemetry event.
 * @param pluginName Unique FlowMVI plugin identifier; defaults to [COOKIELESS_ANALYTICS_PLUGIN_NAME].
 * @param mapIntentToEvent Pure mapper from an intent to an event, or `null` when no event applies.
 */
public fun <S : MVIState, I : MVIIntent, A : MVIAction, E : Any> StoreBuilder<S, I, A>.installAnalyticsPlugin(
    sink: suspend (E) -> Unit,
    pluginName: String = COOKIELESS_ANALYTICS_PLUGIN_NAME,
    mapIntentToEvent: (intent: I) -> E?,
) {
    install(
        plugin {
            name = pluginName
            onIntent { intent ->
                val event = mapIntentToEvent(intent)
                if (event != null) {
                    launch {
                        try {
                            sink(event)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // Best-effort telemetry must never crash or stall store intent processing.
                        }
                    }
                }
                intent
            }
        },
    )
}

/**
 * Side effect: installs a non-blocking FlowMVI plugin passing [platform] and the current
 * [ViewportTier] from [viewportTierProvider] alongside each intent into [mapIntentToEvent].
 *
 * Non-cancellation exceptions thrown by [sink] are swallowed so telemetry failures never block or
 * crash the store; [CancellationException] propagates per structured concurrency.
 *
 * @param sink Suspending function receiving each mapped telemetry event.
 * @param platform Fixed [ClientPlatform] identifying the host runtime.
 * @param viewportTierProvider Pure query evaluated at intent dispatch time for the active viewport tier.
 * @param pluginName Unique FlowMVI plugin identifier; defaults to [COOKIELESS_ANALYTICS_PLUGIN_NAME].
 * @param mapIntentToEvent Pure mapper from intent, platform, and viewport tier to an event, or `null`.
 */
public fun <S : MVIState, I : MVIIntent, A : MVIAction, E : Any> StoreBuilder<S, I, A>.installAnalyticsPlugin(
    sink: suspend (E) -> Unit,
    platform: ClientPlatform,
    viewportTierProvider: () -> ViewportTier,
    pluginName: String = COOKIELESS_ANALYTICS_PLUGIN_NAME,
    mapIntentToEvent: (intent: I, platform: ClientPlatform, viewportTier: ViewportTier) -> E?,
) {
    installAnalyticsPlugin(
        sink = sink,
        pluginName = pluginName,
        mapIntentToEvent = { intent ->
            mapIntentToEvent(intent, platform, viewportTierProvider())
        },
    )
}

/**
 * Side effect: [AnalyticsEventSink] overload of [installAnalyticsPlugin] dispatching non-null
 * mapped events asynchronously without blocking intent reduction.
 *
 * @param analyticsSink Telemetry sink receiving each mapped event.
 * @param pluginName Unique FlowMVI plugin identifier; defaults to [COOKIELESS_ANALYTICS_PLUGIN_NAME].
 * @param mapIntentToEvent Pure mapper from an intent to an event, or `null` when no event applies.
 */
public fun <S : MVIState, I : MVIIntent, A : MVIAction, E : Any> StoreBuilder<S, I, A>.installAnalyticsPlugin(
    analyticsSink: AnalyticsEventSink<E>,
    pluginName: String = COOKIELESS_ANALYTICS_PLUGIN_NAME,
    mapIntentToEvent: (intent: I) -> E?,
) {
    installAnalyticsPlugin(
        sink = analyticsSink::recordEvent,
        pluginName = pluginName,
        mapIntentToEvent = mapIntentToEvent,
    )
}

/**
 * Side effect: [AnalyticsEventSink] overload of [installAnalyticsPlugin] injecting [platform] and
 * [viewportTierProvider] into [mapIntentToEvent].
 *
 * @param analyticsSink Telemetry sink receiving each mapped event.
 * @param platform Fixed [ClientPlatform] identifying the host runtime.
 * @param viewportTierProvider Pure query evaluated at intent dispatch time for the active viewport tier.
 * @param pluginName Unique FlowMVI plugin identifier; defaults to [COOKIELESS_ANALYTICS_PLUGIN_NAME].
 * @param mapIntentToEvent Pure mapper from intent, platform, and viewport tier to an event, or `null`.
 */
public fun <S : MVIState, I : MVIIntent, A : MVIAction, E : Any> StoreBuilder<S, I, A>.installAnalyticsPlugin(
    analyticsSink: AnalyticsEventSink<E>,
    platform: ClientPlatform,
    viewportTierProvider: () -> ViewportTier,
    pluginName: String = COOKIELESS_ANALYTICS_PLUGIN_NAME,
    mapIntentToEvent: (intent: I, platform: ClientPlatform, viewportTier: ViewportTier) -> E?,
) {
    installAnalyticsPlugin(
        sink = analyticsSink::recordEvent,
        platform = platform,
        viewportTierProvider = viewportTierProvider,
        pluginName = pluginName,
        mapIntentToEvent = mapIntentToEvent,
    )
}
