package domain.client

class ClientMessageHandler {

    suspend operator fun invoke(text: String): Result = when (text) {
        CLOSE_CONNECTION -> Result.CloseConnection
        else -> Result.Message(text)
    }

    sealed class Result {
        object CloseConnection : Result()
        class Message(val text: String) : Result()
    }

    companion object {
        const val CLOSE_CONNECTION = "\\close"
    }
}
