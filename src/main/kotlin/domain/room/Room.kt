package domain.room

import java.net.Socket

class Room(val id: Int) {
    private val clients = hashMapOf<String, Socket>()

    override fun equals(other: Any?): Boolean {
        return other is Room && other.id == id
    }

    override fun hashCode(): Int = id.hashCode()

}