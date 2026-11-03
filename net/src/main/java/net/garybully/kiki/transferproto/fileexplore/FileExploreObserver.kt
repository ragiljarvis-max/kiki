package net.garybully.kiki.transferproto.fileexplore

import net.garybully.kiki.transferproto.fileexplore.model.SendMsgReq

interface FileExploreObserver {

    fun onNewState(state: FileExploreState)

    fun onNewMsg(msg: SendMsgReq)
}