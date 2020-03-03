package domain.server

import domain.Message
import domain.room.Room
import domain.sharedEntities.Connection
import domain.utils.MessageSerializer
import kotlinx.coroutines.*
import java.io.File
import java.lang.Exception
import java.lang.NullPointerException
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class Server(port: Int) {

    private val serverSocket = ServerSocket(port)
    private val messageSerializer = MessageSerializer()
    private val serverCommandsHandler = ServerCommandsHandler()

    private val chatsScope = CoroutineScope(Job())

    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")

    private val rooms = hashMapOf<String, Room>()
    private val usersAndRooms = mutableMapOf<String, Room?>()

    suspend fun init() = coroutineScope {
        launch { listenConsoleCommands() }
        launch { awaitClientConnections() }
    }

    private suspend fun awaitClientConnections() {
        while (true) {
            when (val connection = getConnection()) {
                is Connection.UserConnection -> proceedClient(connection)
                is Connection.UploadConnection -> uploadFile(connection)
                is Connection.DownloadConnection -> downloadFile(connection)
            }
        }
    }

    private suspend fun listenConsoleCommands() = withContext(Dispatchers.IO) {
        while (true) {
            when (val command = serverCommandsHandler(readLine()!!)) {
                is ServerCommandsHandler.Result.GetClientsListInRoom -> listClientsInRoom(command.roomName)
                is ServerCommandsHandler.Result.UnknownCommand -> println(UNKNOWN_COMMAND)
            }
        }
    }

    private suspend fun proceedClient(clientInfo: Connection.UserConnection) {
        if (usersAndRooms.containsKey(clientInfo.userName)) {
            rejectUser(clientInfo)
        } else {
            usersAndRooms[clientInfo.userName] = null
            chatsScope.launch {
                listenClient(clientInfo)
            }
            clientInfo.socket.sendMessage(Message(SERVER, "You now can join or create a room"))
        }
    }

    private fun rejectUser(clientInfo: Connection.UserConnection) {
        clientInfo.socket.sendMessage(Message(SERVER, USER_EXISTS_MESSAGE))
        clientInfo.socket.close()
    }

    private fun createNewRoom(clientInfo: Connection.UserConnection, roomName: String) {

        if (rooms.containsKey(roomName)) {
            clientInfo.socket.sendMessage(Message(SERVER, "Room $roomName already exists"))
        } else {
            val room = Room(roomName).apply { clients[clientInfo.userName] = clientInfo.socket }
            rooms[roomName] = room
            usersAndRooms[clientInfo.userName] = room
            clientInfo.socket.sendMessage(Message(SERVER, "New room \"$roomName\" created"))
        }
    }

    private fun connectClientIntoRoom(clientInfo: Connection.UserConnection, roomName: String) {

        if (usersAndRooms[clientInfo.userName] != null) {
            clientInfo.socket.sendMessage(Message(SERVER, "You are already connected to room $roomName"))
            return
        }

        rooms[roomName]?.let { room ->
            room.clients[clientInfo.userName] = clientInfo.socket
            usersAndRooms[clientInfo.userName] = room
            sendBroadcast(BroadcastEvent.NewUserConnected(clientInfo.userName), room.name)
            clientInfo.socket.sendMessage(Message(SERVER, "You have successfully connected to room $roomName"))
        } ?: clientInfo.socket.sendMessage(Message(SERVER, "Room $roomName does not exist"))

    }

    private fun Socket.sendMessage(message: Message) {
        getOutputStream()
            .bufferedWriter()
            .runCatching {
                write(messageSerializer.serialize(message) + '\n')
                flush()
            }
    }

    private fun sendBroadcast(broadcast: BroadcastEvent, roomName: String) {

        val broadcastTime = dateFormatter.format(Date(broadcast.intendedTimestamp))

        val broadcastMessage = with(broadcast) {
            when (broadcast) {
                is BroadcastEvent.NewUserConnected -> "$intendedUserName entered the chat at $broadcastTime"
                    .toMessage()
                is BroadcastEvent.UserDisconnected -> "$intendedUserName left the chat at $broadcastTime"
                    .toMessage()
                is BroadcastEvent.NewMessage -> broadcast.message
            }
        }
        for (client in rooms[roomName]
            ?.clients
            ?.entries
            ?.filter { client -> client.key != broadcast.intendedUserName }
            ?: throw NullPointerException("Room is not yet existing")) {
            client.value.sendMessage(broadcastMessage)
        }
    }

    private suspend fun listenClient(clientInfo: Connection.UserConnection) = withContext(Dispatchers.IO) {
        clientInfo.socket.getInputStream()
            .bufferedReader()
            .use {
                while (true) {
                    try {
                        val rawData = it.readLine()
                        val message = messageSerializer.deserialize(rawData)
                        handleClientMessage(clientInfo, message)
                    } catch (e: Exception) {
                        onUserDisconnected(clientInfo)
                        return@withContext
                    }
                }
            }
    }

    private fun listClientsInRoom(roomName: String) = getClientsInRoom(roomName).forEach(::println)

    private fun getClientsInRoom(roomName: String): Set<String> = rooms[roomName]?.clients?.keys ?: setOf()

    private fun getAllClients(): Set<String> = usersAndRooms.keys

    private fun onUserDisconnected(clientInfo: Connection.UserConnection) {

        usersAndRooms[clientInfo.userName]?.let { room ->
            sendBroadcast(BroadcastEvent.UserDisconnected(clientInfo.userName), room.name)
        }

        usersAndRooms[clientInfo.userName]?.clients?.remove(clientInfo.userName)
        usersAndRooms.remove(clientInfo.userName)
    }

    private fun handleClientMessage(clientInfo: Connection.UserConnection, message: Message) {

        println("Message received: $message")

        if (!handleClientCommand(clientInfo, message)) {

            val userRoom = usersAndRooms[clientInfo.userName]
            if (userRoom != null) {
                sendBroadcast(BroadcastEvent.NewMessage(clientInfo.userName, message), userRoom.name)
            }
        }
    }

    private fun handleClientCommand(clientInfo: Connection.UserConnection, message: Message): Boolean {

        when (val command = serverCommandsHandler(message.textData)) {
            is ServerCommandsHandler.Result.UnknownCommand -> return false
            is ServerCommandsHandler.Result.CreateRoom -> createNewRoom(clientInfo, command.roomName)
            is ServerCommandsHandler.Result.JoinRoom -> connectClientIntoRoom(clientInfo, command.roomName)
            is ServerCommandsHandler.Result.QuitRoom -> quitRoom(clientInfo)
            is ServerCommandsHandler.Result.GetAllCLients -> clientInfo.socket.sendMessage(
                Message(SERVER, getAllClients().joinToString(", "))
            )
            is ServerCommandsHandler.Result.GetClientsListInRoom ->
                clientInfo.socket.sendMessage(
                    Message(SERVER, getClientsInRoom(command.roomName).joinToString(", "))
                )
        }
        return true
    }


    private suspend fun uploadFile(uploadConnection: Connection.UploadConnection) = withContext(Dispatchers.IO) {
        val uploadSocket = uploadConnection.socket
        val room = usersAndRooms[uploadConnection.userName]
        val user = usersAndRooms[uploadConnection.userName]
            ?.clients
            ?.get(uploadConnection.userName)
        val file = File("Uploadings", uploadConnection.fileName)

        if (room == null || user == null) {
            uploadSocket.close()
            return@withContext
        }

        file.createNewFile()

        try {
            uploadSocket.getInputStream()
                .use { it.copyTo(file.outputStream()) }

            room.fileNames.add(file.name)

        } catch (e: Exception) {
            user.sendMessage(Message(SERVER, "Error occured while uploading ${uploadConnection.fileName}, try again"))
        }
    }

    private suspend fun downloadFile(downloadConnection: Connection.DownloadConnection) = withContext(Dispatchers.IO) {
        val downloadSocket = downloadConnection.socket
        val room = usersAndRooms[downloadConnection.userName]
        val user = usersAndRooms[downloadConnection.userName]
            ?.clients
            ?.get(downloadConnection.userName)

        if (room == null || user == null) {
            downloadSocket.close()
            return@withContext
        }

        try {
            val file = File("Uploadings", downloadConnection.fileName)
            if (!file.exists() || !room.fileNames.contains(downloadConnection.fileName)) {
                throw FileDoesNotExistingException(downloadConnection.fileName)
            }

            downloadSocket.getOutputStream().use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            user.sendMessage(
                Message(
                    SERVER,
                    "Error occured while downloading ${downloadConnection.fileName}, try again"
                )
            )
        }
    }

    private fun quitRoom(clientInfo: Connection.UserConnection) {

        val room = usersAndRooms[clientInfo.userName]

        if (room == null) {
            clientInfo.socket.sendMessage(Message(SERVER, "You are not connected to any room"))
            return
        }

        room.clients.remove(clientInfo.userName)
        usersAndRooms[clientInfo.userName] = null
        clientInfo.socket.sendMessage(Message(SERVER, "You have successfully quit the room ${room.name}"))
        sendBroadcast(BroadcastEvent.UserDisconnected(clientInfo.userName), room.name)
    }

    private fun getConnection(): Connection {
        val socket = serverSocket.accept()
        val reader = socket.getInputStream().bufferedReader()
        val handshakeMessage = reader.readLine()!!

        val splittedMessage = handshakeMessage.split(" ")

        return when {
            handshakeMessage.startsWith("/send") -> {
                Connection.UploadConnection(splittedMessage[1], splittedMessage[2], socket)
            }

            handshakeMessage.startsWith("/download") -> {
                val userName = splittedMessage[1]
                val fileName = splittedMessage[2]
                Connection.DownloadConnection(userName, fileName, socket)
            }

            else -> Connection.UserConnection(handshakeMessage, socket)
        }
    }

    private fun String.toMessage(): Message = Message(SERVER, this)

    companion object {
        const val SERVER = "Server"
        const val USER_EXISTS_MESSAGE = "This user already exists, try another one"
        const val UNKNOWN_COMMAND = "Unknown command"
    }


    sealed class BroadcastEvent(val intendedUserName: String, val intendedTimestamp: Long) {

        class NewUserConnected(intendedUserName: String) : BroadcastEvent(intendedUserName, System.currentTimeMillis())
        class UserDisconnected(intendedUserName: String) : BroadcastEvent(intendedUserName, System.currentTimeMillis())
        class NewMessage(intendedUserName: String, val message: Message) :
            BroadcastEvent(intendedUserName, System.currentTimeMillis())
    }

    class FileDoesNotExistingException(fileName: String) : Exception("File $fileName does not existing in this room")
}
