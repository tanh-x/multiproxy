package proxy

import fetch.ProxyHelpers.instantiateProxyFromIP
import okhttp3.OkHttpClient
import java.io.File

class ProxyMonitor {
    var clientList: List<MeteredProxyClient> = emptyList()
        private set

    constructor(addresses: List<String>, includeLocal: Boolean = true) {
        addresses.map(::instantiateProxyFromIP)
    }

    constructor(inputFile: File, includeLocal: Boolean = true) : this(inputFile.readLines(), includeLocal)
}