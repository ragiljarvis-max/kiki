package net.garybully.kiki

import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.netty.toInetAddress
import net.garybully.kiki.netty.toInt
import net.garybully.kiki.transferproto.SimpleCallback
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSender
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderState
import net.garybully.kiki.transferproto.broadcastconn.model.BroadcastTransferFileReq
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object BroadcastSenderTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val sender = BroadcastSender(
            deviceName = "TestSender",
            log = TestLog
        )
        sender.addObserver(object : BroadcastSenderObserver {

            override fun onNewState(state: BroadcastSenderState) {
                println("Sender state: $state")
            }

            override fun requestTransferFile(
                remoteDevice: RemoteDevice
            ) {
                println("Sender receive transfer request: $remoteDevice")
            }
        })
        sender.startBroadcastSender(
            localAddress = localAddress,
            broadcastAddress = localAddress.getBroadcastAddress().first,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Sender start Success")
                }

                override fun onError(errorMsg: String) {
                    super.onError(errorMsg)
                    println("Sender start fail: $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 50)
        }
    }
}