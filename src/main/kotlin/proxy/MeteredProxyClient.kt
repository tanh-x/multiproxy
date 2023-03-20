package proxy

import fetch.ProxyHelpers.instantiateProxyFromIP
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.jvm.Throws

/**
 * A wrapper for an [OkHttpClient] that monitors the vitals of the proxy server. Can also use
 * a non-proxy local client.
 *
 * @param proxyAddress A string with the proxy address, must be in address:port format.
 *                     An empty string denotes no proxy, and the proxy object will be null.
 * @param timeout Duration before timeout in milliseconds, defaults to 1.5s
 * @param tolerance How sensitive the staleness value is to failed fetches
 * @param threshold The threshold value where we terminate this client from use.
 */
class MeteredProxyClient(
    private val proxyAddress: String,
    private val timeout: Long = 1500,
    private val tolerance: Float = 1 / 10f,
    private val threshold: Float = 0.95f
) {
    /**
     * The [Proxy] object for use wit the client, null if using local address.
     * This attribute is private and immutable.
     */
    private val proxy: Proxy? = instantiateProxyFromIP(proxyAddress)

    /**
     * A value from 0 to 1 that denotes the vitality of the proxy server.
     * A value of 1 denotes a dead proxy, a value of 0 denotes a quality proxy
     */
    var staleness: Float = 0f
        private set

    /**
     * Whether this client is backed by a proxy server, the proxy is immutable
     */
    val hasProxy: Boolean
        get() = proxy != null

    /**
     * Dependency injection with [OkHttpClient]
     */
    val client: OkHttpClient = OkHttpClient.Builder().apply {
        callTimeout(timeout, TimeUnit.MILLISECONDS)
        if (hasProxy) proxy(proxy)
    }.build()

    /**
     * Whether the proxy's staleness has exceeded [threshold].
     * If this is the case, AND other proxies are working normally, then this proxy server
     * should be removed from usage and circulation.
     */
    fun isStale(): Boolean = staleness > threshold

    @Throws(IOException::class)
    fun executeRequest(request: Request): Response = client.newCall(request).execute()

    fun updateStaleness(success: Boolean) {
        staleness = (staleness + tolerance * (if (success) 0 else 1)) / (1 + tolerance)
    }
}