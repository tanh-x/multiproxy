package proxy

import okhttp3.Request
import okhttp3.Response
import proxy.exception.FailedRequestException
import proxy.exception.RateLimitedException
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException

class ProxyMonitor(
    private var clientList: MutableList<ProxyClient>,
    private var timeout: Long = STD_TIMEOUT
) : ProxyManager {
    private var successCount = 0;
    private var index: Int = 0

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

                if (response.code == 429) throw RateLimitedException()
                if (!response.isSuccessful) throw FailedRequestException("Request returned code ${response.code}")
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
        if (e !is RateLimitedException) client.updateStaleness(FAILURE)
        println(when (e) {
            is FailedRequestException -> "$client Failed request. ${e.message}"
            is SocketTimeoutException -> "$client Connection timed out"
            is ProtocolException -> "$client Protocol exception (${e.message})"
            is IOException -> "$client IO exception (${e.message})"
            is NullPointerException -> "$client Response body was null"
            is RateLimitedException -> "$client ${e.message}"
            else -> throw e
        })
    }

    private fun cycleNextClient(): ProxyClient = clientList[index++ % clientList.size]

    fun addClient(address: String): ProxyMonitor = this.apply { clientList.add(ProxyClient(address, timeout)) }
    fun addClient(addresses: Collection<String>): ProxyMonitor = this.apply {
        clientList.addAll(addresses.map { a -> ProxyClient(a, timeout) })
    }

    companion object {
        const val SUCCESS = 0f
        const val FAILURE = 1.6f
        const val STD_TIMEOUT = 1500L

        @JvmStatic
        fun generateStalenessReport(): String {
            return ""
        }
    }
}

