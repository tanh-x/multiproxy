package proxy.monitor

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
    clientList: MutableList<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : RequestDispatcher {

    private var clientList: ConcurrentLinkedDeque<ProxyClient> = ConcurrentLinkedDeque()

    init {
        this.clientList.addAll(clientList)
    }

    private var numClients: Int = clientList.size
    private var index: Int = -1
        private set(value) {
            field = value % numClients
        }

    private var successCount: Int = 0
        private set(value) {
            field = value
            if (field % BENCHMARK_INTERVAL == 0) {
                val deltaTime: Long = System.nanoTime() - benchmarkTimestamp
                println(">AVERAGE TIME PER SUCCESS".padEnd(36) + "| ${deltaTime / 1e6 / BENCHMARK_INTERVAL}ms")
            }
        }

    private var benchmarkTimestamp: Long = System.nanoTime()

    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toMutableList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

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
        println("$client SUCCESS | n# = ${successCount++}")
        client.updateStaleness(SUCCESS_STALENESS_WEIGHT)
        return content
    }

    private fun checkResponseCode(code: Int) {
        when (code) {
            200 -> return
            429 -> throw RateLimitedException()
            500 -> throw EndpointInternalErrorException()
            else -> throw FailedRequestException("Request returned code ${code}")
        }
    }

    private fun handleResponseException(e: Exception, client: ProxyClient) {
        if (e !is RateLimitedException) {
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

                else -> throw e
            }
        )
    }

    private fun removeClient(client: ProxyClient) {
        println("$client [!!] Proxy is stale and has been removed from circulation")
        clientList.remove(client)
        if (clientList.size == 0) throw NetworkError()
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
        const val SUCCESS_STALENESS_WEIGHT: Float = 0f
        const val FAILURE_STALENESS_WEIGHT: Float = 1.5f
        const val STD_TIMEOUT = 1000L
        const val BENCHMARK_INTERVAL = 100
    }
}