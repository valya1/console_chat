package domain.client

import java.io.File

class ClientMessageHandler {

    operator fun invoke(text: String): Result {

        val splittedText = text.split(" ")

        return when {
            splittedText[0] == CLOSE_CONNECTION -> Result.CloseConnection
            splittedText[0] == SEND_FILE && splittedText.size == 2 -> {
                val file = File(splittedText[1])
                if (file.exists()) Result.SendFile(file) else Result.Message(splittedText[0])
            }

            splittedText[0] == DOWNLOAD_FILE && splittedText.size == 2 -> Result.DownloadFile(splittedText[1])

            else -> Result.Message(text)
        }
    }

    sealed class Result {
        object CloseConnection : Result()
        class Message(val text: String) : Result()
        class SendFile(val file: File) : Result()
        class DownloadFile(val fileName: String) : Result()
    }

    companion object {
        const val CLOSE_CONNECTION = "/close"
        const val SEND_FILE = "/send"
        const val DOWNLOAD_FILE = "/download"
    }
}
