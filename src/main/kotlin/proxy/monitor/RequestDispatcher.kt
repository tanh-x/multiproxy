package proxy.monitor

import okhttp3.Request

interface RequestDispatcher {
    fun handle(request: Request): String
    fun handleOrNull(request: Request): String?
}