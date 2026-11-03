package net.garybully.kiki.netty.handlers

import net.garybully.kiki.netty.ByteArrayPool
import net.garybully.kiki.netty.NetByteArray
import net.garybully.kiki.netty.PackageData
import net.garybully.kiki.netty.PackageDataWithAddress
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket

class DatagramDataToPckAddrDataDecoder(
    private val byteArrayPool: ByteArrayPool
) : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is DatagramPacket) {
            val buffer = msg.content()
            try {
                val type = buffer.readInt()
                val messageId = buffer.readLong()
                val bodySize = buffer.writerIndex() - buffer.readerIndex()
                val byteArrayValue = byteArrayPool.get(bodySize)
                buffer.readBytes(byteArrayValue.value, 0, bodySize)
                super.channelRead(
                    ctx, PackageDataWithAddress(
                        receiverAddress = msg.sender(),
                        data = PackageData(type, messageId, NetByteArray(byteArrayValue, bodySize))
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                buffer.clear()
            }
        }
    }
}