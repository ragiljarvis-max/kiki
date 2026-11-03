package net.garybully.kiki.netty.extensions

import net.garybully.kiki.netty.ByteArrayPool
import net.garybully.kiki.netty.PackageData

interface IPackageDataConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>, byteArrayPool: ByteArrayPool): PackageData?
}