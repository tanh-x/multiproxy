package proxy

import io.github.oshai.KotlinLogging
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException

private val logger = KotlinLogging.logger {}

abstract class ProxyMonitor : ProxyManager {
    protected var clientList: MutableList<ProxyClient> = mutableListOf()
    private var index: Int = 0

    constructor(addresses: List<String>, includeLocal: Boolean = true) {
        if (includeLocal) clientList.add(ProxyClient(proxyAddress = ""))
        clientList.addAll(addresses.map(::ProxyClient))
    }

    constructor(inputFile: File, includeLocal: Boolean = true) : this(inputFile.readLines(), includeLocal)

    override fun handle(request: Request): String {
        while (true) {
            val proxyClient: ProxyClient = cycleNextClient()
            try {
                val response: Response = proxyClient.executeRequest(request)
                if (!response.isSuccessful) throw FailedRequestException("Request returned code ${response.code}")
                val content: String = response.body!!.string()
                if (content.isEmpty()) throw FailedRequestException("Response body was empty despite success (${response.code})")
                // If we got here, then the request was successful
                proxyClient.updateStaleness(SUCCESS)
                return content
            } catch (e: Exception) {
                logger.warn(generateWarning(e, proxyClient))
                proxyClient.updateStaleness(FAILURE)
            }
        }
    }

    private fun generateWarning(e: Exception, client: ProxyClient): String {
        return when (e) {
            is FailedRequestException -> "$client: Failed request. ${e.message}"
            is SocketTimeoutException -> "$client: Connection timed out"
            is ProtocolException -> "$client: Protocol exception (${e.message})"
            is IOException -> "$client: IO exception (${e.message})"
            is NullPointerException -> "$client: Response body was null"
            else -> throw e
        }
    }

    private fun cycleNextClient(): ProxyClient = clientList[index++ % clientList.size]

    companion object {
        const val SUCCESS = 0f
        const val FAILURE = 1f
    }
}

