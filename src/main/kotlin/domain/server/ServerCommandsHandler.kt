package domain.server

class ServerCommandsHandler {

    operator fun invoke(command: String): Result = when {
        command.startsWith(KICK_USER_PREFIX) -> Result.KickUser(command.substring(KICK_USER_PREFIX.length))
        command == CLIENTS -> Result.ClientsList
        else -> Result.UnknownCommand
    }

    sealed class Result {
        class KickUser(val userName: String) : Result()
        object ClientsList : Result()
        object UnknownCommand : Result()
    }

    companion object {
        const val KICK_USER_PREFIX = "\\kick"
        const val CLIENTS = "\\clients"
    }
}
