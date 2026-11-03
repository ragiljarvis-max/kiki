package net.garybully.kiki

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tans.tuiutils.dialog.showSimpleCancelableCoroutineResultDialogSuspend
import kotlinx.coroutines.launch
import net.garybully.kiki.file.LOCAL_DEVICE
import net.garybully.kiki.netty.findLocalAddressV4
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.ui.connection.localconnetion.BroadcastReceiverDialog
import net.garybully.kiki.ui.connection.localconnetion.BroadcastSenderDialog
import net.garybully.kiki.ui.filetransport.FileTransportActivity
import net.garybully.kiki.ui.qrcodescan.ScanQrCodeActivity
import net.garybully.kiki.ui.connection.ConnectionActivity
import net.garybully.kiki.ui.apkextractor.ApkExtractorActivity
import net.garybully.kiki.ui.cleaner.CleanerActivity
import net.garybully.kiki.ui.filemanager.FileManagerActivity
import net.garybully.kiki.ui.history.HistoryActivity
import net.garybully.kiki.ui.networkspeed.NetworkSpeedActivity
import net.garybully.kiki.ui.vault.VaultActivity
import net.garybully.kiki.ui.webshare.WebShareActivity
import net.garybully.kiki.ui.connection.HotspotActivity
import net.garybully.kiki.ui.theme.KikiTheme
import net.garybully.kiki.utils.showToastShort
import java.net.InetAddress

/**
 * KIKI home shell: single screen, hero card + big actions + FAB sheet.
 * All existing utilities stay reachable from the sheet (nothing removed).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KikiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        deviceName = LOCAL_DEVICE,
                        localAddress = findLocalAddressV4().firstOrNull(),
                        onReceive = { startReceive() },
                        onSend = { startSend() },
                        onChat = { startActivity(Intent(this, ChatActivity::class.java)) },
                        onQrScan = { startActivity(Intent(this, ScanQrCodeActivity::class.java)) },
                        onLaunch = { cls -> startActivity(Intent(this, cls)) }
                    )
                }
            }
        }
    }

    private fun localAddressOrToast(): InetAddress? {
        val addr = findLocalAddressV4().firstOrNull()
        if (addr == null) {
            showToastShort(getString(R.string.error_toast, "No local address"))
        }
        return addr
    }

    private fun startReceive() {
        val addr = localAddressOrToast() ?: return
        lifecycleScope.launch {
            val device: RemoteDevice? = runCatching {
                supportFragmentManager.showSimpleCancelableCoroutineResultDialogSuspend(
                    BroadcastReceiverDialog(addr)
                )
            }.getOrNull()
            device?.let {
                startActivity(
                    FileTransportActivity.getIntent(
                        context = this@MainActivity,
                        localAddress = addr,
                        remoteAddress = it.remoteAddress.address,
                        remoteDeviceInfo = it.deviceName,
                        isServer = false,
                        requestShareFiles = emptyList()
                    )
                )
            }
        }
    }

    private fun startSend() {
        val addr = localAddressOrToast() ?: return
        lifecycleScope.launch {
            val device: RemoteDevice? = runCatching {
                supportFragmentManager.showSimpleCancelableCoroutineResultDialogSuspend(
                    BroadcastSenderDialog(addr)
                )
            }.getOrNull()
            device?.let {
                startActivity(
                    FileTransportActivity.getIntent(
                        context = this@MainActivity,
                        localAddress = addr,
                        remoteAddress = it.remoteAddress.address,
                        remoteDeviceInfo = it.deviceName,
                        isServer = true,
                        requestShareFiles = emptyList()
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    deviceName: String,
    localAddress: InetAddress?,
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onChat: () -> Unit,
    onQrScan: () -> Unit,
    onLaunch: (Class<*>) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "KIKI 🐱",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    localAddress?.hostAddress ?: "…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Transfer tanpa internet ✨",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Sambungkan dua device di WiFi yang sama. " +
                                "Satu tekan Terima, satu tekan Kirim.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Main actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onReceive,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) { Text("📥 Terima", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = onSend,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) { Text("📤 Kirim", fontWeight = FontWeight.Bold) }
                }

                // Chat
                FilledTonalButton(
                    onClick = onChat,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text("💬 Chat") }

                // Connection method chips
                Text(
                    "Metode koneksi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = onReceive, label = { Text("Broadcast") })
                    AssistChip(
                        onClick = { onLaunch(ConnectionActivity::class.java) },
                        label = { Text("WiFi Direct") }
                    )
                    AssistChip(onClick = onQrScan, label = { Text("Scan QR") })
                }

                Spacer(Modifier.height(88.dp))
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            SheetGroups(
                onReceive = onReceive,
                onSend = onSend,
                onChat = onChat,
                onQrScan = onQrScan,
                onLaunch = onLaunch
            )
        }
    }
}

@Composable
private fun SheetGroups(
    onReceive: () -> Unit,
    onSend: () -> Unit,
    onChat: () -> Unit,
    onQrScan: () -> Unit,
    onLaunch: (Class<*>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
    ) {
        Text(
            "Menu KIKI 🐾",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SheetSection("Aksi") {
            SheetItem("📥", "Terima", onClick = onReceive)
            SheetItem("📤", "Kirim", onClick = onSend)
            SheetItem("💬", "Chat", onClick = onChat)
            SheetItem("📷", "Scan QR", onClick = onQrScan)
            SheetItem("📡", "WiFi Direct", onClick = { onLaunch(ConnectionActivity::class.java) })
        }
        SheetSection("Berkas") {
            SheetItem("📁", "File Manager", onClick = { onLaunch(FileManagerActivity::class.java) })
            SheetItem("🕘", "Riwayat", onClick = { onLaunch(HistoryActivity::class.java) })
        }
        SheetSection("Sistem") {
            SheetItem("🧹", "Cleaner", onClick = { onLaunch(CleanerActivity::class.java) })
            SheetItem("📦", "APK Extractor", onClick = { onLaunch(ApkExtractorActivity::class.java) })
            SheetItem("🔒", "Vault", onClick = { onLaunch(VaultActivity::class.java) })
            SheetItem("⚡", "Speed Test", onClick = { onLaunch(NetworkSpeedActivity::class.java) })
        }
        SheetSection("Jaringan") {
            SheetItem("📶", "Hotspot", onClick = { onLaunch(HotspotActivity::class.java) })
            SheetItem("🌐", "WebShare", onClick = { onLaunch(WebShareActivity::class.java) })
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    content()
}

@Composable
private fun SheetItem(icon: String, title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { Text(icon, fontSize = 20.sp) },
        headlineContent = { Text(title) }
    )
}
