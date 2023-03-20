package proxy

import okhttp3.Request
import okhttp3.Response
import java.io.File

abstract class ProxyMonitor: ProxyManager {
    protected var clientList: MutableList<ProxyClient> = mutableListOf()
    private var index: Int = 0

    constructor(addresses: List<String>, includeLocal: Boolean = true) {
        if (includeLocal) clientList.add(ProxyClient(proxyAddress = ""))
        clientList.addAll(addresses.map(::ProxyClient))
    }

    constructor(inputFile: File, includeLocal: Boolean = true) : this(inputFile.readLines(), includeLocal)

    override fun handle(request: Request): Response {
        return cycleNextClient().executeRequest(request)
    }

    abstract fun dispatch(request: Request)
    fun cycleNextClient(): ProxyClient = clientList[index++ % clientList.size]
}

