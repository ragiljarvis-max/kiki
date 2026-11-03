package net.garybully.kiki.transferproto.broadcastconn.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class BroadcastTransferFileResp(
    val deviceName: String
)