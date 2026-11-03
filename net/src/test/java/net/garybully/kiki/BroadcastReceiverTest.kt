package net.garybully.kiki

import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.transferproto.SimpleCallback
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverState
import net.garybully.kiki.transferproto.broadcastconn.model.BroadcastMsg
import net.garybully.kiki.transferproto.broadcastconn.model.BroadcastTransferFileResp
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object BroadcastReceiverTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val receiver = BroadcastReceiver(
            deviceName = "TestReceiver",
            log = TestLog
        )

        receiver.addObserver(object : BroadcastReceiverObserver {
            override fun onNewState(state: BroadcastReceiverState) {
                println("Receiver state: $state")
            }

            override fun onNewBroadcast(
                remoteDevice: RemoteDevice
            ) {
                println("Receiver receive broadcast: $remoteDevice")
//                receiver.requestFileTransfer(
//                    targetAddress = remoteDevice.remoteAddress.address,
//                    simpleCallback = object : SimpleCallback<BroadcastTransferFileResp> {
//
//                        override fun onError(errorMsg: String) {
//                            println("Receiver request transfer fail: $errorMsg")
//                        }
//
//                        override fun onSuccess(data: BroadcastTransferFileResp) {
//                            println("Receiver request transfer success: $data")
//                        }
//                    }
//                )
            }

            override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {}
        })

        receiver.startBroadcastReceiver(
            localAddress = localAddress,
            broadcastAddress = localAddress.getBroadcastAddress().first,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onError(errorMsg: String) {
                    println("Receiver start error: $errorMsg")
                }

                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Receiver start success")
                }
            }
        )

        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}