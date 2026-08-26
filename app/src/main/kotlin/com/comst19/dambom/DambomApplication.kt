package com.comst19.dambom

import android.app.Application
import androidx.work.Configuration
import com.comst19.dambom.work.LegacyWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DambomApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: LegacyWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
