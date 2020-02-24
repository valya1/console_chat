package domain.client

import domain.Message
import domain.utils.MessageSerializer
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.lang.Exception
import java.net.Socket
import java.net.SocketException

class Client {

    private val name: String

    private lateinit var socket: Socket

    private val messagesHandler = ClientMessageHandler()
    private val messageSerializer = MessageSerializer()

    init {
        print("Enter your username: ")
        name = readLine() ?: "Unkwnown user"
    }

    suspend fun connect(address: String, port: Int) = coroutineScope {
        println("Ожидание соединения...")
        try {
            socket = prepareServerSocket(address, port, 10000)
                .also { println("Соединение установлено") }

        } catch (e: TimeoutCancellationException) {
            print("Время ожидания сервера истекло")
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
                    }
                }
            }
    }

    private suspend fun prepareServerSocket(
        host: String,
        port: Int,
        timeoutMillis: Long
    ): Socket = withTimeout(timeoutMillis) {
        var socket: Socket? = null
        while (socket == null) {
            socket = try {
                Socket(host, port)
                    .also {
                        it.getOutputStream().bufferedWriter().run {
                            write(name + '\n')
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