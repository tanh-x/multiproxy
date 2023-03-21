package proxy.monitor

import okhttp3.Request

interface RequestAsyncDispatcher {
    suspend fun handle(request: Request): String
    suspend fun handle(request: Collection<Request>): List<String>
}