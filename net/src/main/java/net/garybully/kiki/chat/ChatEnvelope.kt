package net.garybully.kiki.chat

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Envelope for KIKI chat messages riding in the stock `SendMsgReq.msg` field.
 * Prefix "KIKI_CHAT\u0000" + json payload. No proto changes, so transfer stays
 * compatible with upstream tFileTransfer.
 */
object ChatEnvelope {

    const val PREFIX = "KIKI_CHAT\u0000"

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter by lazy { moshi.adapter(ChatMessage::class.java) }

    fun encode(msg: ChatMessage): String = PREFIX + adapter.toJson(msg)

    /** Returns null when [raw] is not a KIKI chat envelope (plain message). */
    fun decode(raw: String): ChatMessage? {
        if (!isEnvelope(raw)) return null
        return runCatching { adapter.fromJson(raw.substring(PREFIX.length)) }.getOrNull()
    }

    fun isEnvelope(raw: String): Boolean = raw.startsWith(PREFIX)
}
