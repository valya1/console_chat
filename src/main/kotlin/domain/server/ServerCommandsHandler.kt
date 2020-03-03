package domain.server

class ServerCommandsHandler {

    operator fun invoke(command: String): Result = try {

        when {
            command.startsWith(CLIENTS) -> {
                val splitted = command.split(" ")
                when {
                    splitted.size == 1 -> Result.GetAllCLients
                    splitted.size > 1 -> Result.GetClientsListInRoom(splitted[1])
                    else -> throw NullPointerException()
                }
            }
            command.startsWith(CREATE) -> Result.CreateRoom(command.split(" ")[1])
            command.startsWith(JOIN) -> Result.JoinRoom(command.split(" ")[1])
            command.startsWith(QUIT) -> Result.QuitRoom
            command.startsWith(PUBLISH) -> Result.PublishFile(command.split(" ")[1])
            command.startsWith(DOWNLOAD) -> Result.DownloadFile(command.split(" ")[1])
            else -> Result.UnknownCommand
        }
    } catch (e: Exception) {
        Result.UnknownCommand
    }

    sealed class Result {
        class GetClientsListInRoom(val roomName: String) : Result()
        class CreateRoom(val roomName: String) : Result()
        class JoinRoom(val roomName: String) : Result()
        class PublishFile(val filePath: String) : Result()
        class DownloadFile(val filePath: String) : Result()
        object QuitRoom : Result()
        object GetAllCLients : Result()
        object UnknownCommand : Result()
    }

    companion object {
        const val KICK_USER_PREFIX = "/kick"
        const val CLIENTS = "/clients"

        const val CREATE = "/create"
        const val JOIN = "/join"
        const val QUIT = "/quit"
        const val PUBLISH = "/publish"
        const val DOWNLOAD = "/download"
    }
}
