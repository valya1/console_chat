package domain

import kotlinx.serialization.Serializable
import kotlin.String

@Serializable
data class Message(val senderName: String, val textData: String)