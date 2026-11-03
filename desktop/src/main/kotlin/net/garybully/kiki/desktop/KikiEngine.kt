package net.garybully.kiki.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.garybully.kiki.ILog
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverState
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSender
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderState
import net.garybully.kiki.transferproto.broadcastconn.model.BroadcastTransferFileResp
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.broadcastconn.requestFileTransferSuspend
import net.garybully.kiki.transferproto.broadcastconn.startReceiverSuspend
import net.garybully.kiki.transferproto.broadcastconn.startSenderSuspend
import net.garybully.kiki.transferproto.broadcastconn.waitClose
import net.garybully.kiki.transferproto.fileexplore.FileExplore
import net.garybully.kiki.transferproto.fileexplore.FileExploreRequestHandler
import net.garybully.kiki.transferproto.fileexplore.Handshake
import net.garybully.kiki.transferproto.fileexplore.bindSuspend
import net.garybully.kiki.transferproto.fileexplore.connectSuspend
import net.garybully.kiki.transferproto.fileexplore.handshakeSuspend
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesResp
import net.garybully.kiki.transferproto.fileexplore.model.FileExploreDir
import net.garybully.kiki.transferproto.fileexplore.model.FileExploreFile
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirReq
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirResp
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesResp
import net.garybully.kiki.transferproto.fileexplore.requestDownloadFilesSuspend
import net.garybully.kiki.transferproto.fileexplore.requestScanDirSuspend
import net.garybully.kiki.transferproto.fileexplore.requestSendFilesSuspend
import net.garybully.kiki.transferproto.fileexplore.waitClose
import net.garybully.kiki.transferproto.fileexplore.waitHandshake
import net.garybully.kiki.transferproto.filetransfer.FileDownloader
import net.garybully.kiki.transferproto.filetransfer.FileSender
import net.garybully.kiki.transferproto.filetransfer.FileTransferObserver
import net.garybully.kiki.transferproto.filetransfer.FileTransferState
import net.garybully.kiki.transferproto.filetransfer.model.SenderFile
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

object DesktopLog : ILog {
    override fun d(tag: String, msg: String) = println("D/$tag: $msg")
    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("E/$tag: $msg")
        throwable?.printStackTrace()
    }
}

fun localInetAddress(): InetAddress =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 }
        ?: InetAddress.getLocalHost()

/**
 * Desktop transfer engine: mirrors the Android app wiring (BroadcastReceiver/Sender +
 * FileExplore + FileSender/FileDownloader) without any Android dependency.
 */
class KikiEngine(private val scope: CoroutineScope) {

    var deviceName: String = "KIKI-${System.getProperty("user.name", "desktop")}"

    private var receiver: BroadcastReceiver? = null
    private var sender: BroadcastSender? = null
    private var explore: FileExplore? = null
    private var transfer: Any? = null // FileSender or FileDownloader
    private var mainJob: Job? = null

    /** Devices seen via UDP broadcast (receive mode). */
    @Volatile
    var activeDevices: List<RemoteDevice> = emptyList()
        private set

    @Volatile
    var localAddress: InetAddress? = null
        private set

    @Volatile
    var remoteAddress: InetAddress? = null
        private set

    // UI hooks
    var onStatus: (String) -> Unit = {}
    var onRemoteDir: (ScanDirResp) -> Unit = {}
    var onTransferState: (FileTransferState) -> Unit = {}
    var onTransferProgress: (name: String, done: Long, size: Long) -> Unit = { _, _, _ -> }

    private val transferObserver = object : FileTransferObserver {
        override fun onNewState(s: FileTransferState) {
            onTransferState(s)
            if (s is FileTransferState.Finished || s is FileTransferState.Error) transfer = null
        }
        override fun onStartFile(file: FileExploreFile) = onTransferProgress(file.name, 0, file.size)
        override fun onProgressUpdate(file: FileExploreFile, progress: Long) =
            onTransferProgress(file.name, progress, file.size)
        override fun onEndFile(file: FileExploreFile) {}
    }

    private val shareRoot: File = File(System.getProperty("user.home"))
    val downloadDir: File = File(System.getProperty("user.home"), "Downloads/KIKI").apply { mkdirs() }

    // ---------- Receive mode: desktop is FileExplore server ----------

    fun startReceive() {
        stop()
        mainJob = scope.launch {
            withContext(Dispatchers.IO) {
                val addr = localInetAddress()
                localAddress = addr
                val bc = addr.getBroadcastAddress().first
                val r = BroadcastReceiver(deviceName = deviceName, log = DesktopLog)
                receiver = r
                r.addObserver(object : BroadcastReceiverObserver {
                    override fun onNewState(state: BroadcastReceiverState) {}
                    override fun onNewBroadcast(remoteDevice: RemoteDevice) {}
                    override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                        activeDevices = remoteDevices
                    }
                })
                runCatching { r.startReceiverSuspend(addr, bc) }
                    .onFailure { onStatus("Gagal start receiver: ${it.message}"); return@withContext }

                val fe = FileExplore(
                    log = DesktopLog,
                    scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                        override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp =
                            scanLocalDir(request.requestPath)
                    },
                    sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                        // Remote wants to push files to us: we download from remote.
                        override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp {
                            if (isNew) startDownload(request.sendFiles, activeDevices.firstOrNull()?.remoteAddress?.address)
                            return SendFilesResp(bufferSize = 512 * 1024)
                        }
                    },
                    downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                        // Remote wants to pull files from us: we send from our port.
                        override fun onRequest(isNew: Boolean, request: DownloadFilesReq): DownloadFilesResp {
                            if (isNew) startSend(request.downloadFiles)
                            return DownloadFilesResp(maxConnection = 8)
                        }
                    }
                )
                explore = fe
                runCatching {
                    withTimeout(5000) { fe.bindSuspend(addr) }
                    withTimeout(60000) { fe.waitHandshake() }
                }.onSuccess {
                    onStatus("Terhubung — siap terima file")
                    fe.waitClose()
                    onStatus("Koneksi ditutup")
                }.onFailure {
                    onStatus("Gagal: ${it.message}")
                }
            }
        }
    }

    // ---------- Send mode: desktop is FileExplore client (push or pull) ----------

    fun startSend() {
        stop()
        mainJob = scope.launch {
            withContext(Dispatchers.IO) {
                val addr = localInetAddress()
                localAddress = addr
                val bc = addr.getBroadcastAddress().first
                val s = BroadcastSender(deviceName = deviceName, log = DesktopLog)
                sender = s
                var connected: RemoteDevice? = null
                s.addObserver(object : BroadcastSenderObserver {
                    override fun onNewState(state: BroadcastSenderState) {}
                    override fun requestTransferFile(remoteDevice: RemoteDevice) {
                        connected = remoteDevice
                    }
                })
                runCatching { s.startSenderSuspend(addr, bc) }
                    .onFailure { onStatus("Gagal start sender: ${it.message}"); return@withContext }
                onStatus("Menunggu device lain minta transfer…")
                while (connected == null) delay(300)
                val device = connected!!
                remoteAddress = device.remoteAddress.address
                onStatus("Connect ke ${device.deviceName}…")

                val fe = FileExplore(
                    log = DesktopLog,
                    scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                        override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp =
                            scanLocalDir(request.requestPath)
                    },
                    sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                        override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp {
                            if (isNew) startDownload(request.sendFiles, remoteAddress)
                            return SendFilesResp(bufferSize = 512 * 1024)
                        }
                    },
                    downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                        override fun onRequest(isNew: Boolean, request: DownloadFilesReq): DownloadFilesResp {
                            if (isNew) startSend(request.downloadFiles)
                            return DownloadFilesResp(maxConnection = 8)
                        }
                    }
                )
                explore = fe
                runCatching {
                    var times = 3
                    var ok = false
                    while (times-- > 0 && !ok) {
                        ok = runCatching { withTimeout(5000) { fe.connectSuspend(device.remoteAddress.address) } }.isSuccess
                        if (!ok) delay(300)
                    }
                    check(ok) { "connect gagal" }
                    fe.handshakeSuspend()
                }.onSuccess {
                    onStatus("Terhubung ke ${device.deviceName}")
                }.onFailure {
                    onStatus("Gagal: ${it.message}")
                }
            }
        }
    }

    // ---------- Remote browse (send mode, as client) ----------

    fun browseRemote(path: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { explore?.requestScanDirSuspend(path) }
                    .onSuccess { onRemoteDir(it ?: return@onSuccess) }
                    .onFailure { onStatus("Scan gagal: ${it.message}") }
            }
        }
    }

    fun downloadRemoteFiles(files: List<FileExploreFile>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val addr = remoteAddress ?: return@withContext
                runCatching { explore?.requestDownloadFilesSuspend(files) }
                    .onFailure { onStatus("Request download gagal: ${it.message}") }
                startDownload(files, addr)
            }
        }
    }

    /** Push local files to remote (send mode, as client). */
    fun shareLocalFiles(files: List<File>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val exploreFiles = files.map {
                    FileExploreFile(it.name, it.path, it.length(), it.lastModified())
                }
                runCatching { explore?.requestSendFilesSuspend(exploreFiles, 8) }
                    .onFailure { onStatus("Request send gagal: ${it.message}") }
                startSend(exploreFiles)
            }
        }
    }

    // ---------- transfer primitives ----------

    private fun startSend(files: List<FileExploreFile>) {
        if (files.isEmpty()) return
        val addr = localAddress ?: return
        val senderFiles = files.map { SenderFile(File(it.path), it) }
        val fs = FileSender(files = senderFiles, bindAddress = addr, log = DesktopLog)
        transfer = fs
        fs.addObserver(transferObserver)
        fs.start()
    }

    private fun startDownload(files: List<FileExploreFile>, connect: InetAddress?) {
        if (files.isEmpty()) return
        if (connect == null) {
            onStatus("Alamat remote tidak diketahui — tunggu device muncul di broadcast")
            return
        }
        val fd = FileDownloader(
            downloadDir = downloadDir,
            files = files,
            connectAddress = connect,
            maxConnectionSize = 8L,
            log = DesktopLog
        )
        transfer = fd
        fd.addObserver(transferObserver)
        fd.start()
    }

    private fun scanLocalDir(path: String): ScanDirResp {
        val dir = if (path.isBlank()) shareRoot else File(path)
        val childrenDirs = dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.map {
            FileExploreDir(it.name, it.path, it.list()?.size ?: 0, it.lastModified())
        } ?: emptyList()
        val childrenFiles = dir.listFiles { f -> f.isFile }?.sortedBy { it.name }?.map {
            FileExploreFile(it.name, it.path, it.length(), it.lastModified())
        } ?: emptyList()
        return ScanDirResp(dir.path, childrenDirs, childrenFiles)
    }

    fun stop() {
        runCatching { explore?.closeConnectionIfActive() }
        runCatching { receiver?.closeConnectionIfActive() }
        runCatching { sender?.closeConnectionIfActive() }
        (transfer as? FileSender)?.cancel()
        (transfer as? FileDownloader)?.cancel()
        mainJob?.cancel()
        explore = null
        receiver = null
        sender = null
        transfer = null
        activeDevices = emptyList()
        remoteAddress = null
    }

    fun requestFileTransferFrom(device: RemoteDevice) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val r = receiver ?: return@withContext
                runCatching { r.requestFileTransferSuspend(device.remoteAddress.address) }
                    .onSuccess { resp: BroadcastTransferFileResp -> onStatus("Transfer diminta: ${resp.deviceName}") }
                    .onFailure { onStatus("Request gagal: ${it.message}") }
            }
        }
    }
}
