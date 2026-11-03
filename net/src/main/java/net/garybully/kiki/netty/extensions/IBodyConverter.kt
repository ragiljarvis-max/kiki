package net.garybully.kiki.netty.extensions

import net.garybully.kiki.netty.ByteArrayPool
import net.garybully.kiki.netty.PackageData

interface IBodyConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T?
}