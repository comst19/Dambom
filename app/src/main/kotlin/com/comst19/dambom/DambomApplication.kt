package com.comst19.dambom

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import com.comst19.dambom.work.LegacyWorkerFactory
import com.google.android.play.core.splitcompat.SplitCompat
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DambomApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: LegacyWorkerFactory

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        SplitCompat.install(this)
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
