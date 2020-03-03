package domain.sharedEntities

import java.net.Socket

sealed class Connection {

    abstract val socket: Socket

    data class UserConnection(val userName: String, override val socket: Socket) : Connection()

    data class UploadConnection(val userName: String, val fileName: String, override val socket: Socket) :
        Connection()

    data class DownloadConnection(val userName: String, val fileName: String, override val socket: Socket) :
        Connection()


}
