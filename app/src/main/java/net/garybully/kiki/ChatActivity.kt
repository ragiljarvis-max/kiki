package net.garybully.kiki

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.garybully.kiki.chat.ChatEnvelope
import net.garybully.kiki.chat.ChatHub
import net.garybully.kiki.chat.ChatMessage
import net.garybully.kiki.db.AppDatabase
import net.garybully.kiki.db.ChatMessageEntity
import net.garybully.kiki.db.ContactEntity
import net.garybully.kiki.db.GroupEntity
import net.garybully.kiki.db.GroupMemberEntity
import net.garybully.kiki.file.LOCAL_DEVICE
import net.garybully.kiki.logs.AndroidLog
import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSender
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderObserver
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.broadcastconn.requestFileTransferSuspend
import net.garybully.kiki.transferproto.broadcastconn.startReceiverSuspend
import net.garybully.kiki.transferproto.broadcastconn.startSenderSuspend
import net.garybully.kiki.transferproto.fileexplore.FileExplore
import net.garybully.kiki.transferproto.fileexplore.FileExploreObserver
import net.garybully.kiki.transferproto.fileexplore.FileExploreRequestHandler
import net.garybully.kiki.transferproto.fileexplore.FileExploreState
import net.garybully.kiki.transferproto.fileexplore.bindSuspend
import net.garybully.kiki.transferproto.fileexplore.connectSuspend
import net.garybully.kiki.transferproto.fileexplore.handshakeSuspend
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.DownloadFilesResp
import net.garybully.kiki.transferproto.fileexplore.model.FileExploreFile
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirReq
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirResp
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesReq
import net.garybully.kiki.transferproto.fileexplore.model.SendFilesResp
import net.garybully.kiki.transferproto.fileexplore.model.SendMsgReq
import net.garybully.kiki.transferproto.fileexplore.requestDownloadFilesSuspend
import net.garybully.kiki.transferproto.fileexplore.requestMsgSuspend
import net.garybully.kiki.transferproto.fileexplore.requestSendFilesSuspend
import net.garybully.kiki.transferproto.fileexplore.waitHandshake
import net.garybully.kiki.transferproto.filetransfer.FileDownloader
import net.garybully.kiki.transferproto.filetransfer.FileSender
import net.garybully.kiki.transferproto.filetransfer.FileTransferObserver
import net.garybully.kiki.transferproto.filetransfer.FileTransferState
import net.garybully.kiki.transferproto.filetransfer.model.SenderFile
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.ui.theme.KikiTheme
import net.garybully.kiki.utils.showToastShort
import java.io.File
import java.net.InetAddress
import java.util.UUID

/** Chat screen destinations. */
sealed class ChatDest {
    data object List : ChatDest()
    data class Direct(val deviceId: String, val name: String) : ChatDest()
    data class Group(val groupId: String, val name: String) : ChatDest()
    data object GroupCreate : ChatDest()
}

/**
 * Chat engine: own BroadcastReceiver (discover + accept) + BroadcastSender
 * (presence + accept requests) + one FileExplore per chat connection.
 * Group creator acts as hub holding N client connections.
 */
class ChatEngine(val activity: ChatActivity) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = AppDatabase.getDatabase(activity)

    val hub = ChatHub()

    /** deviceId (ip) -> display name last seen. */
    val onlineDevices: SnapshotStateList<RemoteDevice> = emptyList<RemoteDevice>().toMutableStateList()

    @Volatile
    var localAddress: InetAddress? = null
        private set

    private var receiver: BroadcastReceiver? = null
    private var sender: BroadcastSender? = null

    val downloadDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "KIKI"
    ).apply { mkdirs() }

    fun start() {
        scope.launch {
            val addr = withContext(Dispatchers.IO) { findLocalAddressV4().firstOrNull() }
            if (addr == null) {
                activity.showToast("Tidak ada alamat jaringan lokal")
                return@launch
            }
            localAddress = addr
            startReceiver(addr)
            startSender(addr)
        }
    }

    private suspend fun startReceiver(addr: InetAddress) {
        val r = BroadcastReceiver(deviceName = LOCAL_DEVICE, log = AndroidLog)
        receiver = r
        r.addObserver(object : BroadcastReceiverObserver {
            override fun onNewState(state: net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverState) {}
            override fun onNewBroadcast(remoteDevice: RemoteDevice) {}
            override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                onlineDevices.clear()
                onlineDevices.addAll(remoteDevices)
            }
        })
        runCatching { r.startReceiverSuspend(addr, addr.getBroadcastAddress().first) }
            .onFailure { AndroidLog.e(TAG, "receiver start fail: ${it.message}", it) }
    }

    private suspend fun startSender(addr: InetAddress) {
        val s = BroadcastSender(deviceName = LOCAL_DEVICE, log = AndroidLog)
        sender = s
        s.addObserver(object : BroadcastSenderObserver {
            override fun onNewState(state: net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderState) {}
            override fun requestTransferFile(remoteDevice: RemoteDevice) {
                // Peer wants to connect to us: we become the FileExplore server.
                scope.launch { becomeServer(remoteDevice) }
            }
        })
        runCatching { s.startSenderSuspend(addr, addr.getBroadcastAddress().first) }
            .onFailure { AndroidLog.e(TAG, "sender start fail: ${it.message}", it) }
    }

    // ---------- connections ----------

    /** We tapped a peer: we are the FileExplore client. */
    suspend fun connectTo(device: RemoteDevice) {
        val deviceId = deviceIdOf(device)
        if (hub.isConnected(deviceId)) return
        val addr = device.remoteAddress.address
        val fe = createExplore(deviceId, addr)
        runCatching {
            withContext(Dispatchers.IO) {
                var times = 3
                var ok = false
                while (times-- > 0 && !ok) {
                    ok = runCatching { fe.connectSuspend(addr) }.isSuccess
                    if (!ok) kotlinx.coroutines.delay(200)
                }
                check(ok) { "connect gagal" }
                fe.handshakeSuspend()
            }
        }.onSuccess {
            hub.put(deviceId, fe)
            activity.onChatConnected(deviceId, device.deviceName)
        }.onFailure {
            activity.showToast("Gagal konek ke ${device.deviceName}: ${it.message}")
        }
    }

    /** Peer requested us: we are the FileExplore server. */
    private suspend fun becomeServer(device: RemoteDevice) {
        val deviceId = deviceIdOf(device)
        if (hub.isConnected(deviceId)) return
        val addr = localAddress ?: return
        val fe = createExplore(deviceId, device.remoteAddress.address)
        runCatching {
            withContext(Dispatchers.IO) {
                fe.bindSuspend(addr)
                fe.waitHandshake()
            }
        }.onSuccess {
            hub.put(deviceId, fe)
            activity.onChatConnected(deviceId, device.deviceName)
        }.onFailure {
            AndroidLog.e(TAG, "server accept fail: ${it.message}", it)
        }
    }

    private fun createExplore(deviceId: String, peerAddress: InetAddress): FileExplore {
        val fe = FileExplore(
            log = AndroidLog,
            scanDirRequest = object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
                override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp =
                    ScanDirResp(request.requestPath, emptyList(), emptyList())
            },
            sendFilesRequest = object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
                override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp {
                    if (isNew) {
                        scope.launch { downloadFiles(request.sendFiles, peerAddress, deviceId) }
                    }
                    return SendFilesResp(bufferSize = 512 * 1024)
                }
            },
            downloadFileRequest = object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
                override fun onRequest(isNew: Boolean, request: DownloadFilesReq): DownloadFilesResp {
                    if (isNew) {
                        scope.launch { startSend(request.downloadFiles) }
                    }
                    return DownloadFilesResp(maxConnection = 8)
                }
            }
        )
        fe.addObserver(object : FileExploreObserver {
            override fun onNewState(state: FileExploreState) {}
            override fun onNewMsg(msg: SendMsgReq) {
                scope.launch { handleIncoming(msg, deviceId) }
            }
        })
        return fe
    }
    // ---------- messages ----------

    private suspend fun handleIncoming(msg: SendMsgReq, fromDeviceId: String) {
        val chatMsg = ChatEnvelope.decode(msg.msg)
        if (chatMsg == null) {
            // Plain message from stock tFileTransfer peer: show as text.
            save(
                ChatMessageEntity(
                    msgId = UUID.randomUUID().toString(),
                    threadId = fromDeviceId,
                    isGroup = false,
                    senderId = fromDeviceId,
                    senderName = fromDeviceId,
                    type = ChatMessage.TYPE_TEXT,
                    text = msg.msg,
                    fileName = null,
                    fileSize = null,
                    filePath = null,
                    sendTime = msg.sendTime,
                    isMine = false
                )
            )
            return
        }
        if (!hub.markSeen(chatMsg.msgId)) return
        upsertContact(fromDeviceId, chatMsg.senderName)
        save(chatMsg.toEntity(threadId = chatMsg.groupId ?: fromDeviceId, isGroup = chatMsg.groupId != null, isMine = false))
        // Group: hub fans out to other members.
        if (chatMsg.groupId != null) {
            hub.fanOut(chatMsg, excludeId = fromDeviceId)
        }
    }

    suspend fun sendText(threadId: String, isGroup: Boolean, text: String) {
        val msg = ChatMessage.text(senderId = LOCAL_ID, senderName = LOCAL_DEVICE, text = text, groupId = threadId.takeIf { isGroup })
        hub.markSeen(msg.msgId)
        save(msg.toEntity(threadId, isGroup, isMine = true))
        if (isGroup) hub.fanOut(msg) else {
            hub.deviceIds().firstOrNull { it == threadId }?.let { hub.sendTo(it, msg) }
                ?: activity.showToast("Belum terhubung")
        }
    }

    suspend fun sendFile(threadId: String, isGroup: Boolean, file: File) {
        val msg = ChatMessage.file(LOCAL_ID, LOCAL_DEVICE, file.name, file.length(), threadId.takeIf { isGroup })
        hub.markSeen(msg.msgId)
        save(msg.toEntity(threadId, isGroup, isMine = true))
        if (isGroup) hub.fanOut(msg) else {
            hub.deviceIds().firstOrNull { it == threadId }?.let { hub.sendTo(it, msg) }
        }
        // Push the actual bytes: remote auto-downloads via sendFilesRequest handler.
        val exploreFile = FileExploreFile(file.name, file.path, file.length(), file.lastModified())
        if (isGroup) {
            for (memberId in hub.groupMembers(threadId)) {
                val fe = hub.get(memberId) ?: continue
                runCatching { fe.requestSendFilesSuspend(listOf(exploreFile), 8) }
                startSend(listOf(exploreFile))
            }
        } else {
            val fe = hub.get(threadId)
            if (fe != null) {
                runCatching { fe.requestSendFilesSuspend(listOf(exploreFile), 8) }
                startSend(listOf(exploreFile))
            }
        }
    }

    private suspend fun startSend(files: List<FileExploreFile>) {
        val addr = localAddress ?: return
        val fs = FileSender(
            files = files.map { SenderFile(File(it.path), it) },
            bindAddress = addr,
            log = AndroidLog
        )
        fs.addObserver(object : FileTransferObserver {
            override fun onNewState(s: FileTransferState) {}
            override fun onStartFile(file: FileExploreFile) {}
            override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                scope.launch { updateFileBubble(file.name, progress, "sending", isMine = true) }
            }
            override fun onEndFile(file: FileExploreFile) {
                scope.launch { updateFileBubble(file.name, file.size, "sent", isMine = true) }
            }
        })
        fs.start()
    }

    private suspend fun downloadFiles(files: List<FileExploreFile>, from: InetAddress, deviceId: String) {
        val fd = FileDownloader(
            downloadDir = downloadDir,
            files = files,
            connectAddress = from,
            maxConnectionSize = 8L,
            log = AndroidLog
        )
        fd.addObserver(object : FileTransferObserver {
            override fun onNewState(s: FileTransferState) {}
            override fun onStartFile(file: FileExploreFile) {}
            override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                scope.launch { updateFileBubble(file.name, progress, "sending", isMine = false) }
            }
            override fun onEndFile(file: FileExploreFile) {
                val path = File(downloadDir, file.name).path
                scope.launch { updateFileBubble(file.name, file.size, "sent", isMine = false, path = path) }
            }
        })
        fd.start()
    }

    // ---------- groups ----------

    suspend fun createGroup(name: String, members: List<RemoteDevice>): String {
        val groupId = UUID.randomUUID().toString()
        val memberIds = members.map { deviceIdOf(it) }
        db.chatDao().insertGroup(
            GroupEntity(groupId, name.ifBlank { "Grup" }, System.currentTimeMillis(), createdByMe = true)
        )
        for (m in members) {
            db.chatDao().upsertMember(GroupMemberEntity(groupId, deviceIdOf(m), m.deviceName))
        }
        hub.createGroup(memberIds)
        for (m in members) {
            if (!hub.isConnected(deviceIdOf(m))) connectTo(m)
        }
        db.chatDao().insertMessage(
            ChatMessageEntity(
                msgId = UUID.randomUUID().toString(),
                threadId = groupId,
                isGroup = true,
                senderId = LOCAL_ID,
                senderName = LOCAL_DEVICE,
                type = ChatMessage.TYPE_SYSTEM,
                text = "Grup dibuat: ${members.joinToString { it.deviceName }}",
                fileName = null, fileSize = null, filePath = null,
                sendTime = System.currentTimeMillis(),
                isMine = true
            )
        )
        return groupId
    }

    // ---------- db helpers ----------

    private suspend fun save(e: ChatMessageEntity) = db.chatDao().insertMessage(e)

    private suspend fun upsertContact(deviceId: String, name: String) {
        val old = db.chatDao().getContact(deviceId)
        db.chatDao().upsertContact(
            ContactEntity(
                deviceId = deviceId,
                alias = old?.alias ?: "",
                lastSeenName = name,
                lastSeenTime = System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateFileBubble(fileName: String, progress: Long, state: String, isMine: Boolean, path: String? = null) {
        val target = db.chatDao().getAll().lastOrNull { it.fileName == fileName && it.isMine == isMine } ?: return
        db.chatDao().updateFileState(target.msgId, path ?: target.filePath, state)
    }

    suspend fun renameContact(deviceId: String, alias: String) = db.chatDao().renameContact(deviceId, alias)

    suspend fun renameGroup(groupId: String, name: String) = db.chatDao().renameGroup(groupId, name)

    suspend fun deleteThread(threadId: String) = db.chatDao().deleteThread(threadId)

    fun openFile(entity: ChatMessageEntity) {
        val path = entity.filePath ?: return
        val file = File(path)
        if (!file.exists()) {
            activity.showToast("File tidak ada: $path")
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    fun stop() {
        runCatching { receiver?.closeConnectionIfActive() }
        runCatching { sender?.closeConnectionIfActive() }
        hub.closeAll()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ChatEngine"
        const val LOCAL_ID = "me"
    }
}

private fun ChatMessage.toEntity(threadId: String, isGroup: Boolean, isMine: Boolean) = ChatMessageEntity(
    msgId = msgId,
    threadId = threadId,
    isGroup = isGroup,
    senderId = senderId,
    senderName = senderName,
    type = type,
    text = text,
    fileName = fileName,
    fileSize = fileSize,
    filePath = null,
    sendTime = sendTime,
    isMine = isMine
)

private fun deviceIdOf(device: RemoteDevice): String = device.remoteAddress.address.hostAddress ?: device.deviceName

/** ChatActivity: list + direct + group screens over [ChatEngine]. */
class ChatActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var engine: ChatEngine

    var dest: ChatDest by mutableStateOf(ChatDest.List)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = ChatEngine(this)
        engine.start()
        setContent {
            KikiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        ChatNavHost(
                            engine = engine,
                            dest = dest,
                            onDirect = { id, name -> dest = ChatDest.Direct(id, name) },
                            onGroup = { id, name -> dest = ChatDest.Group(id, name) },
                            onGroupCreate = { dest = ChatDest.GroupCreate },
                            onBack = { dest = ChatDest.List }
                        )
                    }
                }
            }
        }
    }

    fun onChatConnected(deviceId: String, name: String) {
        activityScope.launch {
            AppDatabase.getDatabase(this@ChatActivity).chatDao().upsertContact(
                ContactEntity(deviceId, "", name, System.currentTimeMillis())
            )
            if (dest is ChatDest.List) dest = ChatDest.Direct(deviceId, name)
        }
    }

    fun showToast(msg: String) {
        runOnUiThread { showToastShort(msg) }
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
        activityScope.cancel()
    }
}
