package net.garybully.kiki.transferproto.broadcastconn

import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastReceiverObserver {

    fun onNewState(state: BroadcastReceiverState)

    fun onNewBroadcast(remoteDevice: RemoteDevice)

    fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>)
}