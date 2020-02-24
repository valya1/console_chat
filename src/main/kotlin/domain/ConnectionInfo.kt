package domain

import kotlinx.serialization.Serializable
import java.net.Socket

data class ConnectionInfo(val chatInfo: ChatInfo, val socket: Socket)

@Serializable
data class ChatInfo(val userName: String, val roomId: Int)