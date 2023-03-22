package proxy.monitor

import exception.Endpoint404Exception
import exception.EndpointInternalErrorException
import exception.FailedRequestException
import exception.NetworkError
import exception.RateLimitedException
import okhttp3.Request
import okhttp3.Response
import proxy.client.ProxyClient
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedDeque

abstract class AbstractProxyMonitor(
    clientList: List<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : RequestDispatcher {
    /**
     * A list of [ProxyClient] objects, stored in a thread-safe linked double-ended queue, since
     * we access this list concurrently. We also frequently mutate this list, but only at the
     * endpoints (e.g. the pruning of stale proxy servers). The [ProxyClient] hold a
     * proxied [OkHttpClient] dependency injection, which are themselves immutable.
     */
    private var clientList: ConcurrentLinkedDeque<ProxyClient> = ConcurrentLinkedDeque()

    init {
        // We convert the list to a ConcurrentLinkedDeque
        this.clientList.addAll(clientList)
    }

    /**
     * The number of [ProxyClient]s we have in the list, we will be accessing this number
     * frequently, so we memoize this value and make sure to update it every time we mutate
     * the client list.
     */
    private var numClients: Int = clientList.size

    /**
     * The index counter that we use to cycle through the clientList.
     */
    private var index: Int = -1
        private set(value) {
            field = value % numClients
        }

    /**
     * The number of requests that have already been successfully completed
     */
    private var successCount: Int = 0
        private set(value) {
            field = value
            if (field % BENCHMARK_INTERVAL == 0) {
                val deltaTime: Long = System.nanoTime() - benchmarkTimestamp
                benchmarkTimestamp += deltaTime
                println(">AVERAGE TIME PER SUCCESS".padEnd(36) + "| ${deltaTime / 1e6 / BENCHMARK_INTERVAL}ms")
            }
        }

    /**
     * The timestamp object that will be used to benchmark how performant the system is.
     * Is not atomic, and will be mutated to facilitate calculations non-concurrently.
     */
    private var benchmarkTimestamp: Long = System.nanoTime()

    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

    /**
     * The basis for most functionality in this library. This method cycles through the
     */
    override fun handleOrNull(request: Request): String? {
        val proxyClient: ProxyClient = cycleNextClient()
        var response: Response? = null
        try {
            response = proxyClient.executeRequest(request)
            checkResponseCode(response.code)
            return validateResponse(proxyClient, response)
        } catch (e: Exception) {
            handleResponseException(e, proxyClient)
        } finally {
            response?.close()
        }
        return null
    }

    override fun handle(request: Request): String {
        var content: String? = null
        while (content == null) content = handleOrNull(request)
        return content
    }

    private fun validateResponse(client: ProxyClient, response: Response): String {
        val content: String = response.body!!.string()
        if (content.isEmpty()) throw FailedRequestException("Empty body despite success (${response.code})")

        // If we got here, then the request was successful
        successCount++
        if (successCount % PRINT_INTERVAL == 0) println("$client SUCCESS | n# = $successCount")
        client.updateStaleness(SUCCESS_STALENESS_WEIGHT)
        return content
    }

    private fun checkResponseCode(code: Int) {
        when (code) {
            200 -> return
            404 -> throw Endpoint404Exception()
            429 -> throw RateLimitedException()
            500 -> throw EndpointInternalErrorException()
            else -> throw FailedRequestException("Request returned code ${code}")
        }
    }

    private fun handleResponseException(e: Exception, client: ProxyClient) {
        if (e !is RateLimitedException && e !is Endpoint404Exception) {
            client.updateStaleness(FAILURE_STALENESS_WEIGHT)
            if (client.isStale()) removeClient(client)
        }

        println(
            "$client FAILURE | " +
            when (e) {
                is FailedRequestException -> "Failed request. ${e.message}"
                is SocketTimeoutException -> "Connection timed out"
                is ProtocolException -> "Protocol exception (${e.message})"
                is NullPointerException -> "Response body was null"
                is IOException,
                is RateLimitedException,
                is EndpointInternalErrorException -> e.message

                is Endpoint404Exception -> throw e
                else -> throw e
            }
        )
    }

    private fun removeClient(client: ProxyClient) {
        println("$client [!!] Proxy is stale and has been removed from circulation")
        clientList.remove(client)
        if (clientList.size == 0) throw NetworkError()
        numClients = clientList.size
        // TODO: Wait for network
    }

    open fun generateProxyReport(): String {
        return "Healthy proxies: \n" + clientList.joinToString("\n") { c: ProxyClient -> c.proxyAddress }
    }

    open fun addClient(address: String): AbstractProxyMonitor = this.apply {
        clientList.add(ProxyClient(address, timeout))
        numClients = clientList.size
    }

    open fun addClient(addresses: Collection<String>): AbstractProxyMonitor = this.apply {
        clientList.addAll(addresses.map { a -> ProxyClient(a, timeout) })
        numClients = clientList.size
    }

    protected fun cycleNextClient(): ProxyClient {
        return clientList.elementAt(++index)
    }

    companion object {
        const val PRINT_INTERVAL = 5
        const val SUCCESS_STALENESS_WEIGHT: Float = 0f
        const val FAILURE_STALENESS_WEIGHT: Float = 1.5f
        const val STD_TIMEOUT = 1000L
        const val BENCHMARK_INTERVAL = 100
    }
}