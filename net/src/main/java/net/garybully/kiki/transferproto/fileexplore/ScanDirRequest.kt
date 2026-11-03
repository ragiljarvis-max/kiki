package net.garybully.kiki.transferproto.fileexplore


interface FileExploreRequestHandler<Req, Resp> {

    fun onRequest(isNew: Boolean, request: Req): Resp?
}