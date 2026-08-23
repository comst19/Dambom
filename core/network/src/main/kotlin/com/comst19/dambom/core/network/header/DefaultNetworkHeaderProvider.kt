package com.comst19.dambom.core.network.header

import javax.inject.Inject

class DefaultNetworkHeaderProvider
    @Inject
    constructor() : NetworkHeaderProvider {
        override fun headers(): Map<String, String> = emptyMap()
    }
