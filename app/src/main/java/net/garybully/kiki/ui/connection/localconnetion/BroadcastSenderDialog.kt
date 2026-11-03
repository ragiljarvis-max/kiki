package net.garybully.kiki.ui.connection.localconnetion

import android.view.View
import net.garybully.kiki.R
import net.garybully.kiki.databinding.BroadcastSenderDialogLayoutBinding
import net.garybully.kiki.file.LOCAL_DEVICE
import net.garybully.kiki.logs.AndroidLog
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSender
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastSenderState
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.broadcastconn.startSenderSuspend
import net.garybully.kiki.transferproto.broadcastconn.waitClose
import net.garybully.kiki.utils.showToastShort
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class BroadcastSenderDialog : BaseSimpleCoroutineResultCancelableDialogFragment<Unit, RemoteDevice> {

    private val sender: BroadcastSender by lazy {
        BroadcastSender(
            deviceName = LOCAL_DEVICE,
            log = AndroidLog
        )
    }

    private val localAddress: InetAddress?
    constructor() : super(Unit) {
        localAddress = null
    }

    constructor(localAddress: InetAddress) : super(Unit) {
        this.localAddress = localAddress
    }

    override val layoutId: Int = R.layout.broadcast_sender_dialog_layout

    override fun firstLaunchInitData() {  }

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = BroadcastSenderDialogLayoutBinding.bind(view)
        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }
        launch {
            runCatching {
                // Start broadcast server.
                withContext(Dispatchers.IO) {
                    sender.startSenderSuspend(localAddress, localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                AndroidLog.d(TAG, "Start sender success.")
                sender.addObserver(object : BroadcastSenderObserver {

                    // new client transfer file request.
                    override fun requestTransferFile(remoteDevice: RemoteDevice) {
                        onResult(remoteDevice)
                    }

                    override fun onNewState(state: BroadcastSenderState) {
                        AndroidLog.d(TAG, "BroadcastSender state: $state")
                    }
                })

                // Waiting broadcast server error or closed.
                sender.waitClose()
                onCancel()
                requireActivity().showToastShort("Connection closed")
            }.onFailure {
                onCancel()
                AndroidLog.e(TAG, "Start sender error: ${it.message}")
                requireActivity().showToastShort(R.string.error_toast)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            sender.closeConnectionIfActive()
        }
    }

    companion object {
        private const val TAG = "BroadcastSenderDialog"
    }
}