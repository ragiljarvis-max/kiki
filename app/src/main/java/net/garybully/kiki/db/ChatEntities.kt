package net.garybully.kiki.db

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_message")
data class ChatMessageEntity(
    @PrimaryKey val msgId: String,
    /** Direct: peer device id. Group: group id. */
    val threadId: String,
    val isGroup: Boolean,
    val senderId: String,
    val senderName: String,
    /** "text" | "file" | "system" */
    val type: String,
    val text: String?,
    val fileName: String?,
    val fileSize: Long?,
    /** Local file path once received/sent (nullable). */
    val filePath: String?,
    val sendTime: Long,
    val isMine: Boolean,
    /** "sending" | "sent" | "failed" — file transfer status. */
    val transferState: String = "sent"
)

@Entity(tableName = "contact")
data class ContactEntity(
    @PrimaryKey val deviceId: String,
    /** User-given nickname; falls back to lastSeenName when blank. */
    val alias: String = "",
    val lastSeenName: String = "",
    val lastSeenTime: Long = 0L
)

@Entity(tableName = "chat_group")
data class GroupEntity(
    @PrimaryKey val groupId: String,
    val name: String,
    val createdAt: Long,
    val createdByMe: Boolean
)

@Entity(tableName = "group_member", primaryKeys = ["groupId", "deviceId"])
data class GroupMemberEntity(
    val groupId: String,
    val deviceId: String,
    val deviceName: String
)
