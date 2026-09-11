package com.comst19.dambom.core.data.download

import android.system.ErrnoException
import android.system.OsConstants
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import java.io.IOException

internal fun IOException.storageFailureReason(): DownloadFailureReason =
    if (generateSequence<Throwable>(this) { it.cause }.any { it is ErrnoException && it.errno == OsConstants.ENOSPC }) {
        DownloadFailureReason.INSUFFICIENT_STORAGE
    } else {
        DownloadFailureReason.STORAGE
    }
