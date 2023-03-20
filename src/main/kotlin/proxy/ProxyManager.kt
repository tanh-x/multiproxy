package proxy

import okhttp3.Request
import okhttp3.Response

interface ProxyManager {
    fun handle(request: Request): Response
    fun handle(url: String): Response
}