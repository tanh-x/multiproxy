package proxy.client

import helpers.ProxyHelpers.instantiateProxyFromIP
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.Proxy
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import kotlin.math.round

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
class ProxyClient(
    val proxyAddress: String,
    private val timeout: Long = 1500,
    private val tolerance: Float = 1 / 12f,
    private val initialTolerance: Float = 1 / 3f,
    private val threshold: Float = 0.95f
) {
    /**
     * The [Proxy] object for use with the client, null if using local address.
     * This attribute is private and immutable.
     */
    private val proxy: Proxy? = instantiateProxyFromIP(proxyAddress)

    private val ping: ArrayDeque<Long> = ArrayDeque()
    var averagePing: Long = INITIAL_AVERAGE_PING
        get() = ping.sum() / ROLLING_AVERAGE_WINDOW
        private set

    /**
     * Keeps track of how many requests have been made with this proxy, used for weighting
     * the sensitivity of the staleness value.
     */
    private var counter: Int = 0

    /**
     * A value from 0 to 1 that denotes the vitality of the proxy server.
     * A value of 1 denotes a dead proxy, a value of 0 denotes a quality proxy
     */
    private var staleness: Float = 0.5f
        private set

    /**
     * Whether this client is backed by a proxy server, the proxy is immutable
     */
    private val hasProxy: Boolean
        get() = proxy != null

    /**
     * Dependency injection with [OkHttpClient]
     */
    private val client: OkHttpClient = OkHttpClient.Builder().apply {
        callTimeout(timeout, TimeUnit.MILLISECONDS)
        if (hasProxy) proxy(proxy)
    }.build()

    /**
     * Whether the proxy's staleness has exceeded [threshold].
     * If this is the case, AND other proxies are working normally, then this proxy server
     * should be removed from usage and circulation.
     */
    fun isStale(): Boolean = staleness > threshold

    fun executeRequest(request: Request): Response = client.newCall(request).execute().apply {
        if (ping.size >= ROLLING_AVERAGE_WINDOW) ping.removeLast()
        ping.addFirst(getPing())
        counter++
    }

    fun updateStaleness(value: Float) {
        (if (counter < ROLLING_AVERAGE_WINDOW) initialTolerance else tolerance).let {
            staleness = (staleness + it * value) / (1 + it)
        }
    }

    override fun toString(): String = proxyAddress.padEnd(21) +
    " [${averagePing.coerceAtMost(999)}ms|${round(staleness * 1000) / 10}%]".padEnd(14) + " |"

    private fun Response.getPing(): Long = receivedResponseAtMillis - sentRequestAtMillis

    companion object {
        const val INITIAL_STALENESS = 0.5f
        const val INITIAL_AVERAGE_PING = 300L
        const val ROLLING_AVERAGE_WINDOW = 6
    }
}