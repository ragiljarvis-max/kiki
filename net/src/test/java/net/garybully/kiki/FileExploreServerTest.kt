package net.garybully.kiki

import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.transferproto.fileexplore.FileExplore
import net.garybully.kiki.transferproto.fileexplore.FileExploreObserver
import net.garybully.kiki.transferproto.fileexplore.FileExploreRequestHandler
import net.garybully.kiki.transferproto.fileexplore.FileExploreState
import net.garybully.kiki.transferproto.fileexplore.bindSuspend
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesResp
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirReq
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirResp
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesResp
import net.garybully.kiki.transferproto.fileexplore.model.SendMsgReq
import net.garybully.kiki.transferproto.fileexplore.waitClose
import net.garybully.kiki.transferproto.fileexplore.waitHandshake
import kotlinx.coroutines.runBlocking

object FileExploreServerTest {

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val address = findLocalAddressV4()[0]
        val fileExplore = FileExplore(
            log = TestLog,
            scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp? {
                    return null
                }
            },
            sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp? {
                    return null
                }
            },
            downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                override fun onRequest(
                    isNew: Boolean,
                    request: DownloadFilesReq
                ): DownloadFilesResp? {
                    return null
                }
            }
        )
        fileExplore.bindSuspend(address)
        println("Server: connect success.")
        val handShake = fileExplore.waitHandshake()
        println("Server: handshake success: $handShake")
        fileExplore.waitClose()
        println("Server: closed")
    }
}