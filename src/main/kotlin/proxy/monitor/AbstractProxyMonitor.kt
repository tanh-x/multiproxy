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
    clientList: Collection<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : RequestDispatcher {
    /**
     * A list of [ProxyClient] objects, stored in a thread-safe linked double-ended queue, since
     * we access this list concurrently. We also frequently mutate this list, but only at the
     * endpoints (e.g. the pruning of stale proxy servers). The [ProxyClient] hold a
     * proxied [okhttp3.OkHttpClient] dependency injection, which are themselves immutable.
     */
    private var clientList: ConcurrentLinkedDeque<ProxyClient> = ConcurrentLinkedDeque(clientList)

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
            if (field % BENCHMARK_INTERVAL != 0) return

            val deltaTime: Long = System.nanoTime() - benchmarkTimestamp
            benchmarkTimestamp += deltaTime
            println(">AVERAGE TIME PER SUCCESS".padEnd(36) + "| ${deltaTime / 1e6 / BENCHMARK_INTERVAL}ms")
        }

    /**
     * The timestamp object that will be used to benchmark how performant the system is.
     * Is not atomic, and will be mutated to facilitate calculations non-concurrently.
     */
    private var benchmarkTimestamp: Long = System.nanoTime()

    /**
     * This constructor takes in a list of [String]s, each representing an IP address for the proxy
     * server we are leveraging. The validation of these IP addresses are handled within the constructor
     * of [ProxyClient] before instantiation.
     *
     * @param addresses The list of addresses to instantiate [ProxyClient]s from.
     * @param timeout Duration before timeout.
     */
//    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
//        addresses.map(::ProxyClient).toList(), timeout
//    )

    /**
     * This constructor takes in a [File] that holds a list of IP addresses separated by newlines.
     * It then parses them into a List<String> and calls the constructor that takes in a Collection.
     *
     * @param inputFile The file to read.
     * @param timeout Duration before timeout.
     */
    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines().map(::ProxyClient).toList(),
        timeout
    )

    /**
     * The basis for most functionality in this library. This method cycles through the available
     * proxy clients available in the deque, and tries to use the next client to make the request.
     * If it succeeds, the response body is returned after some basic preprocessing. If something
     * went wrong, exception handling is carried out and printed to the CLI. Either way, the proxy
     * client's staleness value will be updated accordingly.
     *
     * @param request The [Request] object
     * @return The response as a raw string. Can be null if the request failed.
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
//        if (successCount % PRINT_INTERVAL == 0)
        println("$client SUCCESS | n# = $successCount")
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
                        is Endpoint404Exception,
                        is EndpointInternalErrorException -> e.message

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

    /**
     * Lists out the address of every proxy client that hasn't been flagged as stale. Often called at
     * the end after we have already finished making a large number of requests.
     *
     * @return The list of addresses remaining in the list, separated by line.
     */
    open fun generateProxyReport(): String =
        "Healthy proxies: \n" + clientList.joinToString("\n") { c: ProxyClient -> c.proxyAddress }

    /**
     * Adds a new [ProxyClient] to the list from a String representing the IP address of the proxy
     * server we are leveraging. Also sets the timeout attribute of the newly instantiated client.
     */
    open fun addClient(address: String): AbstractProxyMonitor = this.apply {
        clientList.add(ProxyClient(address, timeout))
        numClients = clientList.size
    }

    /**
     * Adds new [ProxyClient]s into the list from a [Collection] of [String]s. Also sets the timeout
     * attribute of the newly instantiated clients.
     *
     * @param addresses The list of addresses.
     */
    open fun addClient(addresses: Collection<String>): AbstractProxyMonitor = this.apply {
        clientList.addAll(addresses.map { a -> ProxyClient(a, timeout) })
        numClients = clientList.size
    }

    /**
     * Cycles through the [ConcurrentLinkedDeque] to get the next client to use. Since we leverage
     * a thread-safe data-structure, the class can be safely used concurrently and asynchronously.
     *
     * @return The next client in the deque.
     */
    private fun cycleNextClient(): ProxyClient = clientList.elementAt(++index)

    companion object {
        const val SUCCESS_STALENESS_WEIGHT: Float = 0f
        const val FAILURE_STALENESS_WEIGHT: Float = 1.5f
        const val STD_TIMEOUT = 1000L
        const val BENCHMARK_INTERVAL = 100
    }
}