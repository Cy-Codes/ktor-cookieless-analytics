package codes.cy.kit.analytics

import kotlinx.serialization.Serializable

/**
 * Client runtime family from which an analytics event originated.
 */
@Serializable
public enum class ClientPlatform {
    /** Browser or WebAssembly web client. */
    WEB,

    /** Android native client. */
    ANDROID,

    /** iOS native client. */
    IOS,

    /** Desktop JVM client. */
    DESKTOP,
}
