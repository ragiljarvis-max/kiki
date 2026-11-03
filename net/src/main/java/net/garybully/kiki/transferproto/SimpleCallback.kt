package net.garybully.kiki.transferproto

interface SimpleCallback<T> {

    fun onError(errorMsg: String) {}

    fun onSuccess(data: T) {}
}