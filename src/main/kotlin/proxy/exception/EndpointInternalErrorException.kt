package proxy.exception

class EndpointInternalErrorException : RuntimeException("Endpoint returned 500: Internal server error") {
}