package domain.server

class ServerCommandsHandler {

    operator fun invoke(command: String): Result = when {
//        command.startsWith(KICK_USER_PREFIX) -> Result.KickUser(command.substring(KICK_USER_PREFIX.length))
        command.startsWith(CLIENTS) -> Result.GetClientsListInRoom(command.split(" ")[1])
        command.startsWith(CREATE) -> Result.CreateRoom(command.split(" ")[1])
        command.startsWith(JOIN) -> Result.JoinRoom(command.split(" ")[1])
        command.startsWith(QUIT) -> Result.QuitRoom
        else -> Result.UnknownCommand
    }

    sealed class Result {
        //        class KickUser(val userName: String) : Result()
        class GetClientsListInRoom(val roomName: String) : Result()
        class CreateRoom(val roomName: String) : Result()
        class JoinRoom(val roomName: String) : Result()
        object QuitRoom : Result()
        object UnknownCommand : Result()
    }

    companion object {
        const val KICK_USER_PREFIX = "\\kick"
        const val CLIENTS = "\\clients"

        const val CREATE = "\\create"
        const val JOIN = "\\join"
        const val QUIT = "\\quit"

    }
}
