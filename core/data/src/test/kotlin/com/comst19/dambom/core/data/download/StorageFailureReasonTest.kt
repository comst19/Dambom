package com.comst19.dambom.core.data.download

import android.system.ErrnoException
import android.system.OsConstants
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageFailureReasonTest {
    @Test
    fun `no space errno survives nested IOException wrappers`() {
        val failure = IOException(IOException(ErrnoException("write", OsConstants.ENOSPC)))

        assertEquals(DownloadFailureReason.INSUFFICIENT_STORAGE, failure.storageFailureReason())
    }

    @Test
    fun `other storage failures are not incorrectly classified as full disk`() {
        assertEquals(
            DownloadFailureReason.STORAGE,
            IOException(ErrnoException("open", OsConstants.EACCES)).storageFailureReason(),
        )
        assertEquals(DownloadFailureReason.STORAGE, IOException("write failed").storageFailureReason())
    }
}
