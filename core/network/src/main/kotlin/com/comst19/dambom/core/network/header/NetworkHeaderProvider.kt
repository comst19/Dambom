package com.comst19.dambom.core.network.header

fun interface NetworkHeaderProvider {
    fun headers(): Map<String, String>
}
