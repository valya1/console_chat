import domain.client.Client
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.SocketException

val ceh = CoroutineExceptionHandler { _, throwable ->
    if (throwable is SocketException) {
        println(throwable.message)
    } else {
        throwable.printStackTrace()
    }
}


fun main() = runBlocking {
    val client = Client()

    launch(ceh) { client.connect(LOCAL_HOST, PORT) }.join()
}