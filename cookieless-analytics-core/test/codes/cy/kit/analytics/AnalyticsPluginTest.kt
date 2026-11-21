package codes.cy.kit.analytics

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import pro.respawn.flowmvi.api.MVIAction
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState
import pro.respawn.flowmvi.api.Store
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait

class AnalyticsPluginTest {

    @Test
    fun `installAnalyticsPlugin dispatches mapped events and skips null mappings`() = runTest {
        val capturedEvents = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())
        val testStore = createSampleStore {
            installAnalyticsPlugin<SampleState, SampleIntent, SampleAction, AnalyticsEventPayload>(
                sink = { event -> capturedEvents.update { existing -> existing + event } },
            ) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    SampleIntent.NoOpTick -> null
                    is SampleIntent.SetTier -> null
                }
            }
        }

        testStore.subscribeAndTest {
            emit(SampleIntent.NoOpTick)
            emit(SampleIntent.OpenItem("alpha-guide"))
            wait()

            assertEquals(2, states.value.processedCount)
            assertEquals("alpha-guide", states.value.lastSlug)
            assertEquals(
                SampleAction.ItemOpened("alpha-guide"),
                withTimeout(WAIT_TIMEOUT_MS) { actions.first() },
            )
        }

        val recorded = capturedEvents.first { it.size == 1 }
        assertEquals(
            listOf(
                AnalyticsEventPayload(
                    eventType = "ITEM_OPENED",
                    path = "/items/alpha-guide",
                ),
            ),
            recorded,
        )
    }

    @Test
    fun `installAnalyticsPlugin injects platform and reads latest viewportTierProvider`() = runTest {
        val activeTier = MutableStateFlow(ViewportTier.COMPACT)
        val capturedEvents = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())
        val sink = AnalyticsEventSink<AnalyticsEventPayload> { event ->
            capturedEvents.update { existing -> existing + event }
        }

        val testStore = createSampleStore(onSetTier = { tier -> activeTier.value = tier }) {
            installAnalyticsPlugin(
                analyticsSink = sink,
                platform = ClientPlatform.ANDROID,
                viewportTierProvider = { activeTier.value },
            ) { intent, platform, tier ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "PAGE_VIEW",
                        path = "/items/${intent.slug}",
                        platform = platform,
                        viewportTier = tier,
                    )
                    SampleIntent.NoOpTick,
                    is SampleIntent.SetTier -> null
                }
            }
        }

        testStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("first"))
            emit(SampleIntent.SetTier(ViewportTier.EXPANDED))
            emit(SampleIntent.OpenItem("second"))
            wait()
        }

        val recorded = capturedEvents.first { it.size == 2 }
        assertEquals(
            listOf(
                AnalyticsEventPayload(
                    eventType = "PAGE_VIEW",
                    path = "/items/first",
                    platform = ClientPlatform.ANDROID,
                    viewportTier = ViewportTier.COMPACT,
                ),
                AnalyticsEventPayload(
                    eventType = "PAGE_VIEW",
                    path = "/items/second",
                    platform = ClientPlatform.ANDROID,
                    viewportTier = ViewportTier.EXPANDED,
                ),
            ),
            recorded,
        )
    }

    @Test
    fun `installAnalyticsPlugin is non-blocking while sink suspends`() = runTest {
        val sinkGate = CompletableDeferred<Unit>()
        val sinkStarted = CompletableDeferred<Unit>()
        val completedEvents = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())

        val testStore = createSampleStore {
            installAnalyticsPlugin<SampleState, SampleIntent, SampleAction, AnalyticsEventPayload>(
                sink = { event ->
                    sinkStarted.complete(Unit)
                    sinkGate.await()
                    completedEvents.update { existing -> existing + event }
                },
            ) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    else -> null
                }
            }
        }

        testStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("non-blocking"))
            wait()

            // Reducer completed and updated state even before sinkGate is released.
            assertEquals("non-blocking", states.value.lastSlug)
            assertEquals(1, states.value.processedCount)
            assertEquals(
                SampleAction.ItemOpened("non-blocking"),
                withTimeout(WAIT_TIMEOUT_MS) { actions.first() },
            )
            withTimeout(WAIT_TIMEOUT_MS) { sinkStarted.await() }
            assertTrue(completedEvents.value.isEmpty())

            sinkGate.complete(Unit)
            val finished = withTimeout(WAIT_TIMEOUT_MS) {
                completedEvents.first { it.size == 1 }
            }
            assertEquals("/items/non-blocking", finished.single().path)
        }
    }

    @Test
    fun `installAnalyticsPlugin swallows sink exceptions without crashing the store`() = runTest {
        val capturedEvents = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())

        val testStore = createSampleStore {
            installAnalyticsPlugin<SampleState, SampleIntent, SampleAction, AnalyticsEventPayload>(
                sink = { event ->
                    if (event.path == "/items/fail") {
                        throw IllegalStateException("Telemetry endpoint unavailable")
                    }
                    capturedEvents.update { existing -> existing + event }
                },
            ) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    else -> null
                }
            }
        }

        testStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("fail"))
            emit(SampleIntent.OpenItem("recover"))
            emit(SampleIntent.NoOpTick)
            wait()

            assertEquals(3, states.value.processedCount)
            assertEquals("recover", states.value.lastSlug)
        }

        val recorded = withTimeout(WAIT_TIMEOUT_MS) {
            capturedEvents.first { it.size == 1 }
        }
        assertEquals("/items/recover", recorded.single().path)
    }

    @Test
    fun `installAnalyticsPlugin supports positional sink lambda and AnalyticsEventSink without ambiguity`() = runTest {
        val lambdaCaptured = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())
        val interfaceCaptured = MutableStateFlow<List<AnalyticsEventPayload>>(emptyList())
        val sinkInterface = AnalyticsEventSink<AnalyticsEventPayload> { event ->
            interfaceCaptured.update { existing -> existing + event }
        }

        val lambdaStore = createSampleStore {
            installAnalyticsPlugin({ event: AnalyticsEventPayload ->
                lambdaCaptured.update { existing -> existing + event }
            }) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    else -> null
                }
            }
        }

        val interfaceStore = createSampleStore {
            installAnalyticsPlugin(sinkInterface) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    else -> null
                }
            }
        }

        lambdaStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("lambda-positional"))
            wait()
        }
        interfaceStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("interface-positional"))
            wait()
        }

        assertEquals(
            "/items/lambda-positional",
            withTimeout(WAIT_TIMEOUT_MS) { lambdaCaptured.first { it.size == 1 } }.single().path,
        )
        assertEquals(
            "/items/interface-positional",
            withTimeout(WAIT_TIMEOUT_MS) { interfaceCaptured.first { it.size == 1 } }.single().path,
        )
    }

    @Test
    fun `installAnalyticsPlugin propagates CancellationException during store shutdown`() = runTest {
        val cancellationObserved = CompletableDeferred<Boolean>()
        val sinkEntered = CompletableDeferred<Unit>()

        val testStore = createSampleStore {
            installAnalyticsPlugin<SampleState, SampleIntent, SampleAction, AnalyticsEventPayload>(
                sink = { _ ->
                    sinkEntered.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                    } catch (cancelled: CancellationException) {
                        cancellationObserved.complete(true)
                        throw cancelled
                    }
                },
            ) { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> AnalyticsEventPayload(
                        eventType = "ITEM_OPENED",
                        path = "/items/${intent.slug}",
                    )
                    else -> null
                }
            }
        }

        testStore.subscribeAndTest {
            emit(SampleIntent.OpenItem("in-flight"))
            withTimeout(WAIT_TIMEOUT_MS) { sinkEntered.await() }
        }

        assertTrue(withTimeout(WAIT_TIMEOUT_MS) { cancellationObserved.await() })
    }

    private fun createSampleStore(
        onSetTier: (ViewportTier) -> Unit = {},
        configurePlugin: pro.respawn.flowmvi.dsl.StoreBuilder<SampleState, SampleIntent, SampleAction>.() -> Unit,
    ): Store<SampleState, SampleIntent, SampleAction> =
        store(initial = SampleState()) {
            configure {
                name = "SampleAnalyticsStore"
                allowTransientSubscriptions = true
            }
            configurePlugin()
            reduce { intent ->
                when (intent) {
                    is SampleIntent.OpenItem -> {
                        updateState {
                            copy(processedCount = processedCount + 1, lastSlug = intent.slug)
                        }
                        action(SampleAction.ItemOpened(intent.slug))
                    }
                    SampleIntent.NoOpTick -> {
                        updateState { copy(processedCount = processedCount + 1) }
                    }
                    is SampleIntent.SetTier -> {
                        onSetTier(intent.tier)
                        updateState { copy(processedCount = processedCount + 1) }
                    }
                }
            }
        }

    private companion object {
        const val WAIT_TIMEOUT_MS: Long = 2_000L
    }
}

private data class SampleState(
    val processedCount: Int = 0,
    val lastSlug: String? = null,
) : MVIState

private sealed interface SampleIntent : MVIIntent {
    data class OpenItem(val slug: String) : SampleIntent
    data object NoOpTick : SampleIntent
    data class SetTier(val tier: ViewportTier) : SampleIntent
}

private sealed interface SampleAction : MVIAction {
    data class ItemOpened(val slug: String) : SampleAction
}
