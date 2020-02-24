package domain.server

import domain.Message
import domain.utils.MessageSerializer
import kotlinx.coroutines.*
import java.lang.Exception
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class Server(port: Int) {

    private val serverSocket = ServerSocket(port)
    private val messageSerializer = MessageSerializer()
    private val serverCommandsHandler = ServerCommandsHandler()

    private val chatsScope = CoroutineScope(Job())

    private val dateFormatter = SimpleDateFormat("dd:MM:yyyy HH:mm:ss")


    private val clients = hashMapOf<String, Socket>()


    suspend fun init() = coroutineScope {
        launch { listenCommands() }
        launch { awaitClientConnections() }
    }

    private suspend fun awaitClientConnections() {
        while (clients.count() < 50) {
            val (userName, client) = getClient()
            proceedClient(userName, client)
        }
    }

    private suspend fun listenCommands() = withContext(Dispatchers.IO) {
        while (true) {
            when (val command = serverCommandsHandler(readLine()!!)) {
                is ServerCommandsHandler.Result.KickUser -> kickUser(command.userName)
                is ServerCommandsHandler.Result.ClientsList -> listClients()
                is ServerCommandsHandler.Result.UnknownCommand -> println(UNKNOWN_COMMAND)
            }
        }
    }

    private fun kickUser(userName: String) {
        clients[userName.trim()]
            ?.run {
                close()
                println("User $userName has been kicked")
            }
            ?: println("User $userName does not exist")
    }

    private suspend fun proceedClient(userName: String, socket: Socket) {
        clients[userName.trim()]
            ?.let {
                sendMessage(Message(SERVER, USER_EXISTS_MESSAGE), socket)
                socket.close()
            }
            ?: proceedNewClient(userName, socket)
    }

    private suspend fun proceedNewClient(userName: String, socket: Socket) {
        sendBroadcast(BroadcastEvent.NewUserConnected(userName))
        clients[userName] = socket
        chatsScope.launch {
            startChat(userName, socket)
        }
    }

    private fun sendMessage(message: Message, socket: Socket) {
        socket.getOutputStream()
            .bufferedWriter()
            .runCatching {
                write(messageSerializer.serialize(message) + '\n')
                flush()
            }
    }

    private fun sendBroadcast(broadcast: BroadcastEvent) {

        val broadcastTime = dateFormatter.format(Date(broadcast.intendedTimestamp))

        val broadcastMessage = with(broadcast) {
            when (broadcast) {
                is BroadcastEvent.NewUserConnected -> "$intendedUserName entered the chat at $broadcastTime".toMessage()
                is BroadcastEvent.UserDisconnected -> "$intendedUserName left the chat at $broadcastTime".toMessage()
                is BroadcastEvent.NewMessage -> broadcast.message
            }
        }
        for (client in clients.filter { it.key != broadcast.intendedUserName }.values) {
            sendMessage(broadcastMessage, client)
        }
    }

    private suspend fun startChat(userName: String, socket: Socket) = withContext(Dispatchers.IO) {
        socket.getInputStream()
            .bufferedReader()
            .use {
                while (true) {
                    try {
                        val rawData = it.readLine()
                        val message = messageSerializer.deserialize(rawData)
                        handleClientMessage(userName, message, socket)
                    } catch (e: Exception) {
                        onUserDisconnected(userName)
                        return@withContext
                    }
                }
            }
    }

    private fun listClients() = clients.keys.forEach {
        println(it)
    }

    private fun onUserDisconnected(userName: String) = sendBroadcast(BroadcastEvent.UserDisconnected(userName))

    private fun handleClientMessage(userName: String, message: Message, socket: Socket) {

        println("Message received: $message")

        if (serverCommandsHandler(message.textData) is ServerCommandsHandler.Result.ClientsList) {
            sendMessage(Message(SERVER, clients.keys.joinToString(separator = ", ")), socket)
        } else {
            sendBroadcast(BroadcastEvent.NewMessage(userName, message))
        }
    }

    private fun getClient(): Pair<String, Socket> {
        val socket = serverSocket.accept()
        val userName = socket.getInputStream().bufferedReader().readLine()
        return Pair(userName, socket)
    }

    private fun String.toMessage(): Message = Message(SERVER, this)

    companion object {
        const val SERVER = "Server"
        const val USER_EXISTS_MESSAGE = "This user name is busy, try another one"
        const val UNKNOWN_COMMAND = "Unknown command"
    }


    sealed class BroadcastEvent(val intendedUserName: String, val intendedTimestamp: Long) {

        class NewUserConnected(intendedUserName: String) : BroadcastEvent(intendedUserName, System.currentTimeMillis())
        class UserDisconnected(intendedUserName: String) : BroadcastEvent(intendedUserName, System.currentTimeMillis())
        class NewMessage(intendedUserName: String, val message: Message) :
            BroadcastEvent(intendedUserName, System.currentTimeMillis())
    }
}
