package proxy

class FailedRequestException(
    message: String = "Request was not successful"
) : RuntimeException(message) {
}