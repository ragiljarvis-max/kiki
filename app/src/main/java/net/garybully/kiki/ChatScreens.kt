package net.garybully.kiki

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.garybully.kiki.chat.ChatMessage
import net.garybully.kiki.db.ChatMessageEntity
import net.garybully.kiki.db.ContactEntity
import net.garybully.kiki.db.GroupEntity
import net.garybully.kiki.ui.theme.KikiBubbleMine
import net.garybully.kiki.ui.theme.KikiBubbleTheirs
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.toSizeString
import java.io.File

@Composable
fun ChatNavHost(
    engine: ChatEngine,
    dest: ChatDest,
    onDirect: (String, String) -> Unit,
    onGroup: (String, String) -> Unit,
    onGroupCreate: () -> Unit,
    onBack: () -> Unit
) {
    when (dest) {
        is ChatDest.List -> ChatListScreen(engine, onDirect, onGroup, onGroupCreate)
        is ChatDest.Direct -> ChatScreen(
            engine = engine,
            threadId = dest.deviceId,
            isGroup = false,
            title = dest.name,
            onBack = onBack
        )
        is ChatDest.Group -> ChatScreen(
            engine = engine,
            threadId = dest.groupId,
            isGroup = true,
            title = dest.name,
            onBack = onBack
        )
        is ChatDest.GroupCreate -> GroupCreateScreen(engine, onBack) { id, name ->
            onGroup(id, name)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListScreen(
    engine: ChatEngine,
    onDirect: (String, String) -> Unit,
    onGroup: (String, String) -> Unit,
    onGroupCreate: () -> Unit
) {
    val db = engine.db
    val contacts by db.chatDao().observeContacts().collectAsStateWithLifecycle(emptyList())
    val groups by db.chatDao().observeGroups().collectAsStateWithLifecycle(emptyList())
    val allMessages by db.chatDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val online = engine.onlineDevices
    val listScope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chat 💬") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onGroupCreate) { Text("＋", fontSize = 20.sp) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Online devices (discovered via broadcast)
            if (online.isNotEmpty()) {
                item {
                    Text(
                        "Online sekarang",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(online) { device ->
                    val id = device.remoteAddress.address.hostAddress ?: device.deviceName
                    ListRow(
                        icon = "🟢",
                        title = device.deviceName,
                        subtitle = "Ketuk untuk chat",
                        onClick = {
                            listScope.launch { engine.connectTo(device) }
                            onDirect(id, device.deviceName)
                        }
                    )
                }
            }

            if (groups.isNotEmpty()) {
                item {
                    Text(
                        "Grup",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(groups) { g: GroupEntity ->
                    val last = allMessages.lastOrNull { it.threadId == g.groupId }
                    ListRow(
                        icon = "👥",
                        title = g.name,
                        subtitle = last?.preview() ?: "Belum ada pesan",
                        onClick = { onGroup(g.groupId, g.name) }
                    )
                }
            }

            if (contacts.isNotEmpty()) {
                item {
                    Text(
                        "Kontak",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(contacts) { c: ContactEntity ->
                    val last = allMessages.lastOrNull { it.threadId == c.deviceId }
                    ListRow(
                        icon = "🐱",
                        title = c.alias.ifBlank { c.lastSeenName.ifBlank { c.deviceId } },
                        subtitle = last?.preview() ?: "Ketuk untuk chat",
                        onClick = { onDirect(c.deviceId, c.alias.ifBlank { c.lastSeenName }) }
                    )
                }
            }

            if (contacts.isEmpty() && groups.isEmpty() && online.isEmpty()) {
                item {
                    Text(
                        "Belum ada chat. Pastikan device lain membuka KIKI → Chat, " +
                            "lalu tekan device di daftar online.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun ChatMessageEntity.preview(): String = when (type) {
    ChatMessage.TYPE_FILE -> "📎 ${fileName ?: "file"}"
    else -> text ?: ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    engine: ChatEngine,
    threadId: String,
    isGroup: Boolean,
    title: String,
    onBack: () -> Unit
) {
    val db = engine.db
    val messages by db.chatDao().observeThread(threadId).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                for (uri in uris) {
                    val file = uri.copyToCache(engine.activity) ?: continue
                    engine.sendFile(threadId, isGroup, file)
                }
            }
        }
    }

    // Auto-scroll to newest.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Chat" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = { showRename = true }) { Text("⋮") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg -> Bubble(msg, isGroup) { engine.openFile(msg) } }
            }

            // Input row
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                        Text("📎", fontSize = 20.sp)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tulis pesan…") },
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                input = ""
                                scope.launch { engine.sendText(threadId, isGroup, text) }
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(
            initial = if (isGroup) title else "",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                scope.launch {
                    if (isGroup) engine.renameGroup(threadId, name)
                    else engine.renameContact(threadId, name)
                }
            }
        )
    }
}

@Composable
private fun Bubble(msg: ChatMessageEntity, isGroup: Boolean, onFileClick: () -> Unit) {
    val isMine = msg.isMine
    val bubbleColor = if (isMine) KikiBubbleMine else KikiBubbleTheirs
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(Modifier.padding(10.dp)) {
                if (isGroup && !isMine) {
                    Text(
                        msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (msg.type == ChatMessage.TYPE_FILE) {
                    Row(
                        Modifier.clickable(enabled = msg.filePath != null, onClick = onFileClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📎", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                msg.fileName ?: "file",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                buildString {
                                    append((msg.fileSize ?: 0L).toSizeString())
                                    if (msg.transferState == "sending") append(" · mengirim…")
                                    if (msg.filePath == null && msg.transferState != "sending") append(" · belum diunduh")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(msg.text ?: "")
                }
                Text(
                    timeFormat(msg.sendTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti nama") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupCreateScreen(
    engine: ChatEngine,
    onBack: () -> Unit,
    onCreated: (String, String) -> Unit
) {
    val online = engine.onlineDevices
    val selected = remember { mutableStateListOf<String>() }
    var name by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grup baru 👥") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama grup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Pilih anggota (harus online di Chat):",
                style = MaterialTheme.typography.labelLarge
            )
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(online) { device ->
                    val id = device.remoteAddress.address.hostAddress ?: device.deviceName
                    val isSelected = id in selected
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selected.remove(id) else selected.add(id)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isSelected) "☑" else "☐", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(device.deviceName)
                        }
                    }
                }
                if (online.isEmpty()) {
                    item {
                        Text(
                            "Tidak ada device online. Minta mereka buka KIKI → Chat.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val members = online.filter {
                            (it.remoteAddress.address.hostAddress ?: it.deviceName) in selected
                        }
                        if (members.isEmpty()) return@launch
                        val groupId = engine.createGroup(name, members)
                        onCreated(groupId, name.ifBlank { "Grup" })
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buat grup") }
        }
    }
}

private fun timeFormat(time: Long): String {
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return fmt.format(java.util.Date(time))
}

/** Copy a picked document (content uri) into cache so it can be sent. */
private fun android.net.Uri.copyToCache(context: android.content.Context): File? {
    return runCatching {
        val dir = File(context.cacheDir, "kiki_share").apply { mkdirs() }
        val name = lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
        val out = File(dir, name)
        context.contentResolver.openInputStream(this)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("cannot open uri")
        out
    }.onFailure { it.printStackTrace() }.getOrNull()
}
