package net.garybully.kiki.transferproto.qrscanconn.model

enum class QrScanDataType(val type: Int) {
    TransferFileReq(0),
    TransferFileResp(1)
}