package net.garybully.kiki.ui.connection.localconnetion

import android.view.View
import net.garybully.kiki.R
import net.garybully.kiki.databinding.BroadcastReceiverDialogLayoutBinding
import net.garybully.kiki.databinding.RemoteServerItemLayoutBinding
import net.garybully.kiki.file.LOCAL_DEVICE
import net.garybully.kiki.logs.AndroidLog
import net.garybully.kiki.netty.getBroadcastAddress
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverObserver
import net.garybully.kiki.transferproto.broadcastconn.BroadcastReceiverState
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.broadcastconn.requestFileTransferSuspend
import net.garybully.kiki.transferproto.broadcastconn.startReceiverSuspend
import net.garybully.kiki.transferproto.broadcastconn.waitCloseSuspend
import net.garybully.kiki.utils.showToastShort
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.builders.plus
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class BroadcastReceiverDialog : BaseSimpleCoroutineResultCancelableDialogFragment<BroadcastReceiverDialog.Companion.BroadcastReceiverDialogState, RemoteDevice> {

    private val receiver by lazy {
        BroadcastReceiver(deviceName = LOCAL_DEVICE, log = AndroidLog)
    }

    override val layoutId: Int = R.layout.broadcast_receiver_dialog_layout

    private val localAddress: InetAddress?

    constructor() : super(BroadcastReceiverDialogState()) {
        localAddress = null
    }

    constructor(localAddress: InetAddress) : super(BroadcastReceiverDialogState()) {
        this.localAddress = localAddress
    }

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = BroadcastReceiverDialogLayoutBinding.bind(view)

        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }

        launch {
            runCatching {
                // Start search broadcast server task.
                withContext(Dispatchers.IO) {
                    receiver.startReceiverSuspend(localAddress, localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                receiver.addObserver(object : BroadcastReceiverObserver {
                    override fun onNewBroadcast(
                        remoteDevice: RemoteDevice
                    ) {
                        // onResult(remoteDevice)
                    }
                    override fun onNewState(state: BroadcastReceiverState) {
                        AndroidLog.d(TAG, "BroadcastReceiver State: $state")
                    }

                    // Broadcast server update.
                    override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                        AndroidLog.d(TAG, "RemoteDevices update: $remoteDevices")
                        updateState { it.copy(remoteDevices = remoteDevices) }
                    }
                })
                // Waiting search broadcast server task closed or error.
                receiver.waitCloseSuspend()
                onCancel()
                withContext(Dispatchers.Main.immediate) {
                    requireActivity().showToastShort("Connection closed")
                }
            }.onFailure {
                onCancel()
                withContext(Dispatchers.Main.immediate) {
                    requireActivity().showToastShort(R.string.error_toast)
                }
            }
        }
        val devicesAdapterBuilder = SimpleAdapterBuilderImpl<RemoteDevice>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_server_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow.map { it.remoteDevices }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = RemoteServerItemLayoutBinding.bind(itemView)
                itemViewBinding.remoteDeviceTv.text = data.deviceName
                itemViewBinding.ipAddressTv.text = data.remoteAddress.address.toString().removePrefix("/")
                itemViewBinding.root.clicks(
                    coroutineScope = this,
                    clickWorkOn = Dispatchers.IO) {
                    runCatching {
                        // Request transfer files with broadcast server.
                        receiver.requestFileTransferSuspend(data.remoteAddress.address)
                    }.onSuccess {
                        onResult(data)
                    }.onFailure {
                        AndroidLog.e(TAG, "Request transfer error: ${it.message}", it)
                        withContext(Dispatchers.Main) {
                            requireActivity().showToastShort(requireActivity().getString(R.string.error_toast, it.message))
                        }
                    }
                }
            }
        )

        val emptyAdapterBuilder = SimpleAdapterBuilderImpl<Unit>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_server_empty_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow.map { if (it.remoteDevices.isEmpty()) listOf(Unit) else emptyList() }),
            dataBinder = DataBinderImpl { _, _, _ -> }
        )
        viewBinding.serversRv.adapter = (devicesAdapterBuilder + emptyAdapterBuilder).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            receiver.closeConnectionIfActive()
        }
    }

    companion object {

        private const val TAG = "BroadcastReceiverDialog"

        data class BroadcastReceiverDialogState(
            val remoteDevices: List<RemoteDevice> = emptyList()
        )
    }
}