package com.comst19.dambom.feature.library.subtitle.runtime

import android.content.Context
import android.content.IntentSender
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
internal class SubtitleRuntimeDelivery
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val manager = SplitInstallManagerFactory.create(context)
        private val installation = Mutex()
        private val mutableConfirmation = MutableStateFlow<IntentSender?>(null)
        val confirmation = mutableConfirmation.asStateFlow()

        fun installed(): Boolean = MODULE_NAME in manager.installedModules

        fun confirmationLaunched() {
            mutableConfirmation.value = null
        }

        @Suppress("CyclomaticComplexMethod")
        suspend fun install() =
            installation.withLock {
                if (installed()) return@withLock
                suspendCancellableCoroutine<Unit> { continuation ->
                    var session = 0
                    lateinit var listener: SplitInstallStateUpdatedListener

                    fun cleanup() {
                        manager.unregisterListener(listener)
                        mutableConfirmation.value = null
                    }

                    fun fail(error: Exception) {
                        cleanup()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                    listener =
                        SplitInstallStateUpdatedListener { state: SplitInstallSessionState ->
                            if (MODULE_NAME in state.moduleNames() && (session == 0 || state.sessionId() == session)) {
                                when (state.status()) {
                                    SplitInstallSessionStatus.INSTALLED -> {
                                        cleanup()
                                        SplitCompat.install(context)
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }

                                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                                        mutableConfirmation.value = state.resolutionIntent()?.intentSender
                                    }

                                    SplitInstallSessionStatus.FAILED, SplitInstallSessionStatus.CANCELED -> {
                                        fail(IOException("Runtime install failed: ${state.errorCode()}"))
                                    }
                                }
                            }
                        }
                    manager.registerListener(listener)
                    continuation.invokeOnCancellation {
                        cleanup()
                        if (session != 0) manager.cancelInstall(session)
                    }
                    val request = SplitInstallRequest.newBuilder().addModule(MODULE_NAME).build()
                    manager
                        .startInstall(request)
                        .addOnSuccessListener { id ->
                            session = id
                            if (!continuation.isActive) {
                                manager.cancelInstall(id)
                            } else if (installed()) {
                                cleanup()
                                SplitCompat.install(context)
                                continuation.resume(Unit)
                            }
                        }.addOnFailureListener(::fail)
                }
            }

        fun runtime(): LocalSubtitleLlm {
            check(installed())
            return Class.forName(RUNTIME_CLASS).getDeclaredConstructor().newInstance() as LocalSubtitleLlm
        }
    }

private const val MODULE_NAME = "translationruntime"
private const val RUNTIME_CLASS = "com.comst19.dambom.translationruntime.GemmaRuntime"

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SubtitleRuntimeEntryPoint {
    fun delivery(): SubtitleRuntimeDelivery
}
