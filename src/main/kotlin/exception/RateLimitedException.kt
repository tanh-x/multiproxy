package exception

class RateLimitedException(
    message: String = "Request was not successful, client is being rate limited"
) : RuntimeException(message) {
}