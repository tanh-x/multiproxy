package proxy.monitor

import exception.Endpoint404Exception
import helpers.ProxyHelpers.convert404toNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import proxy.client.ProxyClient
import java.io.File

class ProxyAsyncMonitor(
    clientList: List<ProxyClient>,
    timeout: Long = STD_TIMEOUT
) : AbstractProxyMonitor(clientList, timeout) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

    fun handleAll(
        requests: Collection<Request>,
        batchSize: Int = DFLT_BATCH_SIZE,
    ): List<String> {
        return handleAllOrNull(requests, batchSize, Int.MAX_VALUE).requireNoNulls()
    }

    fun handleAllOrNull(
        requests: Collection<Request>, batchSize: Int = DFLT_BATCH_SIZE, attempts: Int = 1
    ): List<String?> = runBlocking {
        val results: MutableMap<Request, String?> = requests.associateBy({ it }, { null }).toMutableMap()

        repeat(attempts) {
            val nullKeys: Set<Request> = results.filterValues { it == null }.keys
            val newValues: List<String?> = nullKeys.chunked(batchSize)
                .map { batch: List<Request> ->
                    batch.map { req: Request ->
                        scope.async { convert404toNull { handleOrNull(req) } }
                    }.awaitAll()
                }.flatten()

            nullKeys.forEachIndexed { i: Int, req: Request -> results[req] = newValues[i] }
        }

        return@runBlocking results.values.toList()
    }

    companion object {
        const val DFLT_BATCH_SIZE: Int = 16
    }
}
