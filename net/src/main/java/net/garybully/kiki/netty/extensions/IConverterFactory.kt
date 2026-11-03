package net.garybully.kiki.netty.extensions

interface IConverterFactory {

    fun findBodyConverter(type: Int, dataClass: Class<*>) : IBodyConverter?

    fun findPackageDataConverter(type: Int, dataClass: Class<*>): IPackageDataConverter?
}