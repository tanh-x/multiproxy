package proxy.exception

class FailedRequestException(
    message: String = "Request was not successful"
) : RuntimeException(message) {
}