package net.garybully.kiki.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.fileexplore.model.FileExploreDir
import net.garybully.kiki.transferproto.fileexplore.model.FileExploreFile
import net.garybully.kiki.transferproto.fileexplore.model.ScanDirResp
import net.garybully.kiki.transferproto.filetransfer.FileTransferState
import net.garybully.kiki.toSizeString
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "KIKI — Kawaii Inter-device Kommunication Interface") {
        Surface(modifier = Modifier.fillMaxSize()) {
            KikiApp()
        }
    }
}

private enum class Screen { Home, Receive, Send }

@Composable
private fun KikiApp() {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    var screen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var remoteDir by remember { mutableStateOf<ScanDirResp?>(null) }
    var transferState by remember { mutableStateOf("") }
    var transferName by remember { mutableStateOf("") }
    var transferDone by remember { mutableStateOf(0L) }
    var transferSize by remember { mutableStateOf(0L) }
    val selected = remember { mutableStateListOf<FileExploreFile>() }

    val engine = remember {
        if (deviceName.isBlank()) deviceName = KikiEngine(scope).deviceName
        KikiEngine(scope).apply {
            onStatus = { status = it }
            onRemoteDir = { resp -> remoteDir = resp }
            onTransferState = { st ->
                transferState = when (st) {
                    is FileTransferState.Finished -> "Selesai 🎉"
                    is FileTransferState.Error -> "Error: ${st.msg}"
                    is FileTransferState.RemoteError -> "Remote error: ${st.msg}"
                    FileTransferState.Canceled -> "Dibatalkan"
                    else -> transferState
                }
            }
            onTransferProgress = { name, done, size ->
                transferName = name
                transferDone = done
                transferSize = size
            }
        }
    }

    MaterialTheme {
        when (screen) {
            Screen.Home -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("KIKI 🐱", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Kawaii Inter-device Kommunication Interface", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = {
                        deviceName = it
                        engine.deviceName = it.ifBlank { engine.deviceName }
                    },
                    label = { Text("Nama device") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { screen = Screen.Receive; engine.startReceive() }) {
                        Text("📥 Terima")
                    }
                    Button(onClick = { screen = Screen.Send; engine.startSend() }) {
                        Text("📤 Kirim")
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            Screen.Receive -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Mode Terima", style = MaterialTheme.typography.headlineSmall)
                Text("Device lain di jaringan yang sama bisa kirim file ke sini.", style = MaterialTheme.typography.bodyMedium)
                Text("IP: ${engine.localAddress?.hostAddress ?: "…"}", style = MaterialTheme.typography.bodyLarge)
                Text("Nama: ${engine.deviceName}", style = MaterialTheme.typography.bodySmall)
                Text("Folder unduhan: ${engine.downloadDir}", style = MaterialTheme.typography.bodySmall)
                if (engine.activeDevices.isNotEmpty()) {
                    Text("Device terlihat:", style = MaterialTheme.typography.bodyMedium)
                    engine.activeDevices.forEach { d ->
                        Text("• ${d.deviceName} (${d.remoteAddress.address})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TransferCard(transferName, transferDone, transferSize, transferState)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    engine.stop()
                    screen = Screen.Home
                    status = ""
                }) { Text("← Kembali") }
            }

            Screen.Send -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Mode Kirim", style = MaterialTheme.typography.headlineSmall)
                Text(status, style = MaterialTheme.typography.bodyMedium)

                remoteDir?.let { dir ->
                    RemoteBrowser(
                        dir = dir,
                        selected = selected,
                        modifier = Modifier.weight(1f),
                        onOpen = { path -> engine.browseRemote(path) },
                        onDownload = { engine.downloadRemoteFiles(selected.toList()) }
                    )
                }

                Button(onClick = {
                    pickFiles()?.let { files ->
                        if (files.isNotEmpty()) engine.shareLocalFiles(files.toList())
                    }
                }) { Text("📤 Pilih & kirim file lokal") }

                TransferCard(transferName, transferDone, transferSize, transferState)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    engine.stop()
                    screen = Screen.Home
                    status = ""
                }) { Text("← Kembali") }
            }
        }
    }
}

@Composable
private fun TransferCard(name: String, done: Long, size: Long, state: String) {
    if (name.isBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, fontWeight = FontWeight.Medium)
            if (size > 0) {
                LinearProgressIndicator(
                    progress = { (done.toFloat() / size.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${done.toSizeString()} / ${size.toSizeString()}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isNotBlank()) Text(state, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RemoteBrowser(
    dir: ScanDirResp,
    selected: MutableList<FileExploreFile>,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Remote: ${dir.path}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onOpen(File(dir.path).parent ?: dir.path) }) { Text("⬆ Naik") }
            Button(onClick = onDownload, enabled = selected.isNotEmpty()) {
                Text("⬇ Download (${selected.size})")
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            dir.childrenDirs.forEach { d: FileExploreDir ->
                TextButton(onClick = { onOpen(d.path) }) { Text("📁 ${d.name}") }
            }
            dir.childrenFiles.forEach { f: FileExploreFile ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        if (selected.contains(f)) selected.remove(f) else selected.add(f)
                    }) {
                        Text(if (selected.contains(f)) "☑ " else "☐ ")
                    }
                    Text("${f.name}  (${f.size.toSizeString()})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** AWT multi-select file picker (Linux/Windows desktop). */
private fun pickFiles(): Array<File>? {
    val dialog = FileDialog(null as Frame?, "Pilih file untuk dikirim", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    return dialog.files
}
