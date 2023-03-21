package proxy

import okhttp3.Request
import okhttp3.Response
import proxy.exception.EndpointInternalErrorException
import proxy.exception.FailedRequestException
import proxy.exception.NetworkError
import proxy.exception.RateLimitedException
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException

class ProxyMonitor(
    private var clientList: MutableList<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : ProxyManager {
    private var successCount = 0
    private var index: Int = -1
        set(value) {
            field = value % clientList.size
        }

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

                when (response.code) {
                    200 -> {}
                    429 -> throw RateLimitedException()
                    500 -> throw EndpointInternalErrorException()
                    else -> throw FailedRequestException("Request returned code ${response.code}")
                }

                val content: String = response.body!!.string()
                if (content.isEmpty()) throw FailedRequestException("Response body was empty despite success (${response.code})")

                // If we got here, then the request was successful
                println("$proxyClient SUCCESS | n# = ${successCount++}")
                proxyClient.updateStaleness(SUCCESS)
                return content
            } catch (e: Exception) {
                handleResponseException(e, proxyClient)
            } finally {
                response?.close()
            }
        }
    }

    private fun handleResponseException(e: Exception, client: ProxyClient) {
        if (e !is RateLimitedException) {
            client.updateStaleness(FAILURE)
            if (client.isStale()) removeClient(index)
        }

        println(
            "$client FAILURE | " +
            when (e) {
                is FailedRequestException -> "Failed request. ${e.message}"
                is SocketTimeoutException -> "Connection timed out"
                is ProtocolException -> "Protocol exception (${e.message})"
                is IOException -> "IO exception (${e.message})"
                is NullPointerException -> "Response body was null"
                is RateLimitedException -> "${e.message}"
                else -> throw e
            }
        )

    }

    private fun removeClient(i: Int) {
        println("${clientList[i]} [!!] Proxy is stale and has been removed from circulation")
        clientList.removeAt(i)
        if (clientList.size == 0) throw NetworkError()
        // TODO: Wait for network
    }

    fun generateProxyReport(clients: List<ProxyClient> = clientList): String {
        return "Healthy proxies: \n" + clients.joinToString("\n") { c: ProxyClient -> c.proxyAddress }
    }

    fun addClient(address: String): ProxyMonitor = this.apply { clientList.add(ProxyClient(address, timeout)) }
    fun addClient(addresses: Collection<String>): ProxyMonitor = this.apply {
        clientList.addAll(addresses.map { a -> ProxyClient(a, timeout) })
    }

    private fun cycleNextClient(): ProxyClient = clientList[++index]

    companion object {
        const val SUCCESS = 0f
        const val FAILURE = 1.6f
        const val STD_TIMEOUT = 1500L
    }
}

