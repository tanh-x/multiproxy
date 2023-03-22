package proxy.monitor

import okhttp3.Request
import okhttp3.Response
import proxy.client.ProxyClient
import java.io.File

class ProxyMonitor(
    clientList: MutableList<ProxyClient>,
    timeout: Long = STD_TIMEOUT
) : AbstractProxyMonitor(clientList, timeout) {
    constructor(addresses: Collection<String>, timeout: Long = STD_TIMEOUT) : this(
        addresses.map(::ProxyClient).toMutableList(), timeout
    )

    constructor(inputFile: File, timeout: Long = STD_TIMEOUT) : this(
        inputFile.readLines(), timeout
    )
}

