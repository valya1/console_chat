package domain.room

import java.net.Socket

class Room(val name: String) {

    val clients = hashMapOf<String, Socket>()
    val fileNames = mutableSetOf<String>()

    override fun equals(other: Any?): Boolean {
        return other is Room && other.name == name
    }

    override fun hashCode(): Int = name.hashCode()

}