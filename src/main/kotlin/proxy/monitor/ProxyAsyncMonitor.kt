package proxy.monitor

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import proxy.client.ProxyClient
import java.io.File

class ProxyAsyncMonitor(
    clientList: MutableList<ProxyClient>,
    timeout: Long = STD_TIMEOUT
) : AbstractProxyMonitor(clientList, timeout) {
    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toMutableList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

    fun handleAll(
        requests: Collection<Request>,
        batchSize: Int = DFLT_BATCH_SIZE,
    ): List<String> {
        TODO("handle all non null")
    }

    fun handleAllOrNull(
        requests: Collection<Request>, batchSize: Int = DFLT_BATCH_SIZE, attempts: Int = 1,
    ): List<String> = runBlocking {
        val pendingRequests: MutableList<Request> = requests.toMutableList()
        val completedRequest: Map<Request, String?> = requests.associateBy({ it }, { null })
        repeat(attempts) {
            for (i in 0 until pendingRequests.size step batchSize) {
                pendingRequests.slice(i until i + batchSize).map { r: Request ->
                    async { handle(r) }
                }.awaitAll()
            }

            if (null !in completedRequest.values) return@repeat
        }
        return@runBlocking completedRequest.values.requireNoNulls() as List<String>
    }

    companion object {
        const val DFLT_BATCH_SIZE: Int = 16
    }
}

