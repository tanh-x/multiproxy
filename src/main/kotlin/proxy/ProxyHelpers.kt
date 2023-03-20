package proxy

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object ProxyHelpers {
    @JvmStatic
    fun instantiateClient(proxy: Proxy?): OkHttpClient = OkHttpClient.Builder().apply {
        callTimeout(1600, TimeUnit.MILLISECONDS)
        if (proxy != null) proxy(proxy)
    }.build()

    @JvmStatic
    fun instantiateProxyFromIP(ip: String): Proxy? {
        if (ip.isEmpty()) return null  // No proxy, use local IP address
        if (!regexIPAddress.matches(ip)) throw IllegalArgumentException("Not a valid address: $ip")
        val split: List<String> = ip.split(":")
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(split[0], split[1].toInt()))
    }

    @JvmStatic
    fun buildURLRequest(url: String): Request = Request.Builder().url(url).build()


    private val regexIPAddress: Regex = Regex("""[\w.-]+:\d{1,5}""")
}