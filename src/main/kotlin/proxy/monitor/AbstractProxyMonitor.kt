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
import kotlin.math.round

abstract class AbstractProxyMonitor(
    clientList: MutableList<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : RequestDispatcher {
    protected var clientList: MutableList<ProxyClient> = clientList
        private set(value) {
            field = value
            this.numClients = value.size
        }
    private var numClients: Int = clientList.size
    protected var index: Int = -1
        private set(value) {
            field = value % numClients
        }

    protected var successCount: Int = 0
        private set(value) {
            field = value
            if (field % BENCHMARK_INTERVAL == 0) {
                val deltaTime: Long = System.nanoTime() - benchmarkTimestamp
                println("Avg. time per success: ${deltaTime / 1e6 / BENCHMARK_INTERVAL}")
            }
        }

    private var benchmarkTimestamp: Long = System.nanoTime()

    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toMutableList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

    override fun handle(request: Request): String {
        while (true) {
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
        }
    }

    protected fun validateResponse(client: ProxyClient, response: Response): String {
        val content: String = response.body!!.string()
        if (content.isEmpty()) throw FailedRequestException("Empty body despite success (${response.code})")

        // If we got here, then the request was successful
        println("$client SUCCESS | n# = ${successCount++}")
        client.updateStaleness(SUCCESS_STALENESS_WEIGHT)
        return content
    }

    protected fun checkResponseCode(code: Int) {
        when (code) {
            200 -> return
            429 -> throw RateLimitedException()
            500 -> throw EndpointInternalErrorException()
            else -> throw FailedRequestException("Request returned code ${code}")
        }
    }

    protected fun handleResponseException(e: Exception, client: ProxyClient) {
        if (e !is RateLimitedException) {
            client.updateStaleness(FAILURE_STALENESS_WEIGHT)
            if (client.isStale()) removeClient(index)
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

    protected fun removeClient(i: Int) {
        println("${clientList[i]} [!!] Proxy is stale and has been removed from circulation")
        clientList.removeAt(i)
        if (clientList.size == 0) throw NetworkError()
        // TODO: Wait for network
    }

    open fun generateProxyReport(clients: List<ProxyClient> = clientList): String {
        return "Healthy proxies: \n" + clients.joinToString("\n") { c: ProxyClient -> c.proxyAddress }
    }

    open fun addClient(address: String): AbstractProxyMonitor = this.apply { clientList.add(ProxyClient(address, timeout)) }
    open fun addClient(addresses: Collection<String>): AbstractProxyMonitor = this.apply {
        clientList.addAll(addresses.map { a -> ProxyClient(a, timeout) })
    }

    protected fun cycleNextClient(): ProxyClient = clientList[++index]

    companion object {
        const val SUCCESS_STALENESS_WEIGHT: Float = 0f
        const val FAILURE_STALENESS_WEIGHT: Float = 1.5f
        const val STD_TIMEOUT = 1000L
        const val BENCHMARK_INTERVAL = 100
    }
}