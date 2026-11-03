package net.garybully.kiki.transferproto.filetransfer

import net.garybully.kiki.transferproto.fileexplore.model.FileExploreFile

interface FileTransferObserver {

    fun onNewState(s: FileTransferState)

    fun onStartFile(file: FileExploreFile)

    fun onProgressUpdate(file: FileExploreFile, progress: Long)

    fun onEndFile(file: FileExploreFile)
}