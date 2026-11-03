package net.garybully.kiki.ui.connection.localconnetion

import android.view.View
import net.garybully.kiki.R
import net.garybully.kiki.databinding.QrCodeServerDialogBinding
import net.garybully.kiki.file.LOCAL_DEVICE
import net.garybully.kiki.logs.AndroidLog
import net.garybully.kiki.netty.toInt
import net.garybully.kiki.transferproto.TransferProtoConstant
import net.garybully.kiki.transferproto.broadcastconn.model.RemoteDevice
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanServer
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanServerObserver
import net.garybully.kiki.transferproto.qrscanconn.QRCodeScanState
import net.garybully.kiki.transferproto.qrscanconn.model.QRCodeShare
import net.garybully.kiki.transferproto.qrscanconn.startQRCodeScanServerSuspend
import net.garybully.kiki.utils.toJson
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode
import java.net.InetAddress

class QRCodeServerDialog : BaseSimpleCoroutineResultCancelableDialogFragment<Unit, RemoteDevice> {

    private val qrcodeServer: QRCodeScanServer by lazy {
        QRCodeScanServer(log = AndroidLog)
    }

    private val localAddress: InetAddress?
    constructor() : super(Unit) {
        localAddress = null
    }

    constructor(localAddress: InetAddress) : super(Unit) {
        this.localAddress = localAddress
    }

    override val layoutId: Int = R.layout.qr_code_server_dialog

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = QrCodeServerDialogBinding.bind(view)

        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }

        launch(Dispatchers.IO) {
            qrcodeServer.addObserver(object : QRCodeScanServerObserver {

                // Client request transfer file.
                override fun requestTransferFile(remoteDevice: RemoteDevice) {
                    AndroidLog.d(TAG, "Receive request: $remoteDevice")
                    onResult(remoteDevice)
                }

                override fun onNewState(state: QRCodeScanState) {
                    AndroidLog.d(TAG, "Qrcode server state: $state")
                }
            })
            runCatching {
                // Start QR code server connection.
                qrcodeServer.startQRCodeScanServerSuspend(localAddress = localAddress)
            }.onSuccess {
                AndroidLog.d(TAG, "Bind address success.")
                runCatching {
                    // Create QRCode bitmap and display.
                    val qrcodeContent = QRCodeShare(
                        version = TransferProtoConstant.VERSION,
                        deviceName = LOCAL_DEVICE,
                        address = localAddress.toInt()
                    ).toJson()!!
                    QRCode.from(qrcodeContent).withSize(320, 320).bitmap()
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        viewBinding.qrCodeIv.setImageBitmap(it)
                    }
                }.onFailure {
                    AndroidLog.e(TAG, "Create qrcode fail: ${it.message}", it)
                    onCancel()
                }
            }.onFailure {
                AndroidLog.e(TAG, "Bind address: $localAddress fail.")
                onCancel()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            qrcodeServer.closeConnectionIfActive()
        }
    }

    companion object {
        private const val TAG = "QRCodeServerDialog"
    }
}