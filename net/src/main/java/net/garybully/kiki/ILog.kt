package net.garybully.kiki

interface ILog {

    fun d(tag: String, msg: String)

    fun e(tag: String, msg: String, throwable: Throwable? = null)
}