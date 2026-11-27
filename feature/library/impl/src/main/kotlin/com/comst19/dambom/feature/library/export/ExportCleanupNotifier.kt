package com.comst19.dambom.feature.library.export

import android.content.Context
import android.widget.Toast
import com.comst19.dambom.feature.library.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal fun interface ExportCleanupNotifier {
    suspend fun notifyIncompleteCleanup()
}

internal class ToastExportCleanupNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ExportCleanupNotifier {
        override suspend fun notifyIncompleteCleanup() {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.library_export_cleanup_incomplete, Toast.LENGTH_LONG).show()
            }
        }
    }
