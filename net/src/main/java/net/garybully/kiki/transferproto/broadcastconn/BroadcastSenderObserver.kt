package net.garybully.kiki.transferproto.broadcastconn

import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastSenderObserver {

    fun onNewState(state: BroadcastSenderState)

    fun requestTransferFile(remoteDevice: RemoteDevice)
}