package net.garybully.kiki.transferproto.qrscanconn

import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice

interface QRCodeScanServerObserver : QRCodeScanObserver {

    fun requestTransferFile(remoteDevice: RemoteDevice)
}