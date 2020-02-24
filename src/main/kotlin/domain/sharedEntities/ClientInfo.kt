package domain.sharedEntities

import java.net.Socket

data class ClientInfo(val userName: String, val socket: Socket)
