package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.NetworkConnection
import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    val connection: Flow<NetworkConnection>
}
