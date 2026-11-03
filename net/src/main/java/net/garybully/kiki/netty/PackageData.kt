package net.garybully.kiki.netty


data class PackageData(
    val type: Int,
    val messageId: Long,
    val body: NetByteArray
)