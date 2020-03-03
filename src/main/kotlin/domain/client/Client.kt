package domain.client

import domain.Message
import domain.utils.MessageSerializer
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.net.Socket

class Client {

    private val name: String

    private lateinit var socket: Socket

    private var address: String = ""
    private var port: Int = 0

    private val messagesHandler = ClientMessageHandler()


    private val messageSerializer = MessageSerializer()

    init {
        println("Enter your username: ")
        name = readLine() ?: "Unkwnown user"
    }

    suspend fun connect(address: String, port: Int) = coroutineScope {
        println("Waiting for connection...")
        try {
            socket = prepareSocket(address, port, 10_000, name)
                .also {
                    this@Client.address = address
                    this@Client.port = port
                    println("Connection established")
                }

        } catch (e: TimeoutCancellationException) {
            print("Server timeout exceeded")
            return@coroutineScope
        }

        launch { listenServer() }
        launch { handleUserInputs() }
    }


    @Synchronized
    private suspend fun disconnect() = coroutineScope {
        cancel()
        println("Socket closed")
    }

    private suspend fun listenServer() = withContext(Dispatchers.IO) {
        socket.getInputStream()
            .bufferedReader()
            .use {
                while (true) {
                    try {
                        val rawText = it.readLine()
                        val message: Message = messageSerializer.deserialize(rawText)
                        println("${message.senderName}: ${message.textData}")
                    } catch (e: Exception) {
                        disconnect()
                        return@withContext
                    }
                }
            }
    }

    private suspend fun handleUserInputs() = withContext(Dispatchers.IO) {
        socket.getOutputStream()
            .bufferedWriter()
            .use {
                while (true) {
                    val input = readLine()!!
                    when (val handledMessage = messagesHandler(input)) {
                        is ClientMessageHandler.Result.CloseConnection -> {
                            disconnect()
                            return@withContext
                        }
                        is ClientMessageHandler.Result.Message -> it.sendMessage(handledMessage.text)
                        is ClientMessageHandler.Result.SendFile -> uploadFile(handledMessage.file)
                        is ClientMessageHandler.Result.DownloadFile -> downloadFile(handledMessage.fileName)
                    }
                }
            }
    }

    private suspend fun uploadFile(file: File) {
        println("Starting file uploading...")
        withContext(Dispatchers.IO) {
            val socket = prepareSocket(address, port, 5_000, "/send $name ${file.name}")

            println("File uploading...")
            try {
                socket.getOutputStream().use { outputStream ->
                    file.inputStream().use { inputStream ->
                        println("File uploaded, bytes uploaded: ${inputStream.copyTo(outputStream)}!")
                    }
                }
            } catch (e: Exception) {
                println("Error while ${file.name} uploading: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun downloadFile(fileName: String) {
        println("Starting file downloading...")
        withContext(Dispatchers.IO) {
            val socket = prepareSocket(address, port, 5_000, "/download $name $fileName")
            try {
                socket.getInputStream()
                    .use { inputStream ->
                        val outputStream = File(fileName).also { it.createNewFile() }.outputStream()
                        println("File downloaded, bytes: ${inputStream.copyTo(outputStream)}")
                    }
            } catch (e: Exception) {
                println("Error while $fileName downloading: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun prepareSocket(
        host: String,
        port: Int,
        timeoutMillis: Long,
        handshake: String
    ): Socket = withTimeout(timeoutMillis) {
        var socket: Socket? = null
        while (socket == null) {
            socket = try {
                Socket(host, port)
                    .also {
                        it.getOutputStream()
                            .bufferedWriter()
                            .run {
                                write(handshake + '\n')
                                flush()
                            }
                    }
            } catch (e: Exception) {
                null
            }
            delay(100)
        }
        socket!!
    }

    private fun BufferedWriter.sendMessage(messagePayload: String) {
        runCatching {
            write(messageSerializer.serialize(Message(name, messagePayload)) + '\n')
            flush()
        }
    }
}