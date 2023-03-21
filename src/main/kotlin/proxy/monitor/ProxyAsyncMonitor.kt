package proxy.monitor

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
) : AbstractProxyMonitor(clientList, timeout), RequestAsyncDispatcher {
    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toMutableList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )

    override suspend fun handle(request: Request): String = coroutineScope {
        val proxyClient: ProxyClient
    }

    override suspend fun handle(requests: Collection<Request>): List<String> = coroutineScope {
        return@coroutineScope requests.map { async { handle(it) } }.awaitAll()
    }


}

