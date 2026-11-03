package net.garybully.kiki.transferproto.filetransfer.model

import net.garybully.kiki.transferproto.fileexplore.model.FileExploreFile
import java.io.File

data class SenderFile(
    val realFile: File,
    val exploreFile: FileExploreFile
)