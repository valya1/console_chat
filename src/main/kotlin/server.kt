import domain.server.Server
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


const val LOCAL_HOST = "localhost"
const val PORT = 1900

fun main() {
    runBlocking {
        val server = Server(PORT)
        launch(ceh) { server.init() }.join()
    }
}