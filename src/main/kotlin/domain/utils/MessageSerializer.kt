package domain.utils

import domain.Message
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class MessageSerializer {

    private val json = Json(JsonConfiguration.Stable)

    fun deserialize(rawJson: String): Message = json.fromJson(Message.serializer(), json.parseJson(rawJson))
    fun serialize(message: Message): String = json.toJson(Message.serializer(), message).toString()

}

val json = Json(JsonConfiguration.Stable)

fun <T> deserialize(serializer: KSerializer<T>, rawJson: String): T = json.fromJson(serializer, json.parseJson(rawJson))

fun <T> serialize(serializer: KSerializer<T>, item: T): String = json.toJson(serializer, item).toString()