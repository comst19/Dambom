package com.comst19.dambom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SubtitleRuntimeDeliveryTest {
    @Test
    fun onDemandRuntimeLoadsOnlyAfterLocalFeatureInstallation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = SplitInstallManagerFactory.create(context)
        val module = "translationruntime"
        val runtime = "com.comst19.dambom.translationruntime.GemmaRuntime"
        assertFalse("Use a base-only local-testing installation", module in manager.installedModules)
        assertThrows(ClassNotFoundException::class.java) { Class.forName(runtime, true, context.classLoader) }
        val completed = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val listener =
            SplitInstallStateUpdatedListener { state ->
                if (module in state.moduleNames()) {
                    when (state.status()) {
                        SplitInstallSessionStatus.INSTALLED -> {
                            completed.countDown()
                        }

                        SplitInstallSessionStatus.FAILED, SplitInstallSessionStatus.CANCELED -> {
                            error.set("Feature delivery failed: ${state.errorCode()}")
                            completed.countDown()
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            }
        manager.registerListener(listener)
        try {
            manager
                .startInstall(SplitInstallRequest.newBuilder().addModule(module).build())
                .addOnFailureListener {
                    error.set(it.toString())
                    completed.countDown()
                }
            assertTrue("Feature installation timed out", completed.await(60, TimeUnit.SECONDS))
            check(error.get() == null) { checkNotNull(error.get()) }
            assertTrue(module in manager.installedModules)
            assertTrue(SplitCompat.install(context))
            val implementation =
                Class.forName(runtime, true, context.classLoader).getDeclaredConstructor().newInstance()
            assertTrue(implementation.javaClass.interfaces.any { it.simpleName == "LocalSubtitleLlm" })
            Class.forName("com.google.ai.edge.litertlm.Engine", true, context.classLoader)
        } finally {
            manager.unregisterListener(listener)
        }
    }
}
