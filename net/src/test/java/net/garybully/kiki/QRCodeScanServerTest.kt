package net.garybully.kiki

import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.transferproto.SimpleCallback
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanServer
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanServerObserver
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object QRCodeScanServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val server = QRCodeScanServer(log = TestLog)
        server.addObserver(object : QRCodeScanServerObserver {

            override fun requestTransferFile(remoteDevice: RemoteDevice) {
                println("Server receive request: $remoteDevice")
            }

            override fun onNewState(state: QRCodeScanState) {
                println("Server state: $state")
            }

        })
        server.startQRCodeScanServer(
            localAddress = localAddress,
            callback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    println("Server connect success")
                }

                override fun onError(errorMsg: String) {
                    println("Server $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}