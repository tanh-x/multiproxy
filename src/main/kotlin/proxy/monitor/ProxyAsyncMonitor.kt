package proxy.monitor

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

    /**
     * Wrapper for [ProxyAsyncMonitor.handleOrNull], but with infinite attempts (i.e. keep trying to
     * fetch the requests until every response is non-null). Usage of this method should be avoided
     * since it may hang the program indefinitely if the [Request] is faulty by nature. Instead, call
     * [handleOrNull] with a some number of Requests and handle null values as needed.
     *
     * @param requests The list of requests to go over.
     * @param batchSize The size of each chunk of requests. Larger chunks allows more requests to
     * be made concurrently and often results in faster overall fetch time. But depending on the
     * timeout duration, failed requests may substantially diminish the fetch time of each chunk.
     *
     * @return The list of responses. The order of responses in the return value is guaranteed
     * to match the order of [requests].
     */
    fun handleAll(
        requests: Collection<Request>,
        batchSize: Int = DFLT_BATCH_SIZE,
    ): List<String> {
        return handleAllOrNull(requests, batchSize, Int.MAX_VALUE).requireNoNulls()
    }


    /**
     * Automatically handles a large number of [Request]s asynchronously and safely. Splits up
     * the [Collection] (which can be arbitrarily large) into chunks of size [batchSize] as given
     * in the parameter. The [attempts] parameter determines how many passes will be done. Every
     * pass, we select every request that hasn't acquired a response value (keys with null value),
     * then call [AbstractProxyMonitor.handleOrNull] on each chunk. Exception handling and basic
     * response preprocessing is also handled.
     *
     * @param requests The list of requests to go over.
     * @param batchSize The size of each chunk of requests. Larger chunks allows more requests to
     * be made concurrently and often results in faster overall fetch time. But depending on the
     * timeout duration, failed requests may substantially diminish the fetch time of each chunk.
     * @param attempts The number of passes to fill in the null values. When we have already made
     * that many attempts, or if no null keys remain, we exit from the [repeat] body and return
     * the (potentially null) responses.
     *
     * @return The list of responses. The order of responses in the return value is guaranteed
     * to match the order of [requests].
     */
    fun handleAllOrNull(
        requests: Collection<Request>, batchSize: Int = DFLT_BATCH_SIZE, attempts: Int = 1
    ): List<String?> = runBlocking {
        val res: MutableMap<Request, String?> = requests.associateBy({ it }, { null }).toMutableMap()
        repeat(attempts) { _ ->
            val nullKeys: Set<Request> = res.filterValues { it == null }.keys.also { if (it.isEmpty()) return@repeat }
            val newValues: List<String?> = nullKeys.chunked(batchSize).map { batch: List<Request> ->
                batch.map { req: Request -> scope.async { handleOrNull(req) } }.awaitAll()
            }.flatten()
            nullKeys.forEachIndexed { idx: Int, req: Request -> res[req] = newValues[idx] }
        }
        return@runBlocking res.values.toList()
    }

    companion object {
        const val DFLT_BATCH_SIZE: Int = 16
    }
}
