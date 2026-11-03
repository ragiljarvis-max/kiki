package net.garybully.kiki.chat

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/**
 * KIKI chat message. Transported inside the stock `SendMsgReq.msg` field as
 * "KIKI_CHAT\u0000" + json (see [ChatEnvelope]) — proto untouched, stock
 * tFileTransfer peers just render the raw json text.
 */
@Keep
@JsonClass(generateAdapter = true)
data class ChatMessage(
    val msgId: String,
    val type: String,
    val sendTime: Long,
    val senderId: String,
    val senderName: String,
    val groupId: String? = null,
    val text: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    /** Roster snapshot (device ids) so members know who is in the group. */
    val members: List<String>? = null
) {
    val isFile: Boolean get() = type == TYPE_FILE

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_FILE = "file"
        const val TYPE_SYSTEM = "system"

        fun newId(): String = java.util.UUID.randomUUID().toString()

        fun text(senderId: String, senderName: String, text: String, groupId: String? = null): ChatMessage =
            ChatMessage(
                msgId = newId(),
                type = TYPE_TEXT,
                sendTime = System.currentTimeMillis(),
                senderId = senderId,
                senderName = senderName,
                groupId = groupId,
                text = text
            )

        fun file(
            senderId: String,
            senderName: String,
            fileName: String,
            fileSize: Long,
            groupId: String? = null
        ): ChatMessage =
            ChatMessage(
                msgId = newId(),
                type = TYPE_FILE,
                sendTime = System.currentTimeMillis(),
                senderId = senderId,
                senderName = senderName,
                groupId = groupId,
                fileName = fileName,
                fileSize = fileSize
            )

        fun system(senderId: String, senderName: String, text: String, groupId: String? = null): ChatMessage =
            ChatMessage(
                msgId = newId(),
                type = TYPE_SYSTEM,
                sendTime = System.currentTimeMillis(),
                senderId = senderId,
                senderName = senderName,
                groupId = groupId,
                text = text
            )
    }
}
