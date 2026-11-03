package net.garybully.kiki.transferproto.qrscanconn

interface QRCodeScanObserver {

    fun onNewState(state: QRCodeScanState)
}