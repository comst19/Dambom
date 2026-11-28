package com.comst19.dambom.feature.library.file

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowPackageManager
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

internal class RecordingFileProvider : FileProvider() {
    val getTypeThreads = CopyOnWriteArrayList<Thread>()

    override fun getType(uri: Uri): String? {
        getTypeThreads += Thread.currentThread()
        return super.getType(uri)
    }
}

@Suppress("DEPRECATION")
internal fun registerTestFileProvider(context: Context): RecordingFileProvider {
    val authority = "${context.packageName}.fileprovider"
    val packageInfo = PackageInfo().apply { packageName = TEST_PROVIDER_PACKAGE }
    val packageManager = shadowOf(context.packageManager)
    packageManager.installPackage(packageInfo)
    ShadowPackageManager.resources[TEST_PROVIDER_PACKAGE] =
        object : Resources(
            context.assets,
            context.resources.displayMetrics,
            context.resources.configuration,
        ) {
            override fun getXml(id: Int): XmlResourceParser =
                if (id == TEST_PATHS_XML_ID) {
                    xmlResourceParser()
                } else {
                    super.getXml(id)
                }
        }
    val info =
        ProviderInfo().apply {
            this.authority = authority
            this.name = FileProvider::class.java.name
            this.packageName = TEST_PROVIDER_PACKAGE
            this.exported = false
            this.grantUriPermissions = true
            this.metaData = Bundle().apply { putInt(FILE_PROVIDER_PATHS_META_DATA, TEST_PATHS_XML_ID) }
        }
    packageManager.addOrUpdateProvider(info)
    clearFileProviderCache(authority)
    return RecordingFileProvider().also { provider ->
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }
}

private fun clearFileProviderCache(authority: String) {
    val cache =
        FileProvider::class.java
            .getDeclaredField("sCache")
            .apply { isAccessible = true }
            .get(null) as MutableMap<*, *>
    cache.remove(authority)
}

private fun xmlResourceParser(): XmlResourceParser {
    val parser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader(SAVED_VIDEOS_PATHS_XML))
        }
    return Proxy.newProxyInstance(
        XmlResourceParser::class.java.classLoader,
        arrayOf(XmlResourceParser::class.java),
    ) { _, method, args ->
        when (method.name) {
            "close" -> Unit
            else -> method.invoke(parser, *(args ?: emptyArray()))
        }
    } as XmlResourceParser
}

private const val TEST_PROVIDER_PACKAGE = "com.comst19.dambom.test.fileprovider"
private const val FILE_PROVIDER_PATHS_META_DATA = "android.support.FILE_PROVIDER_PATHS"
private const val TEST_PATHS_XML_ID = 0x7f990001
private const val SAVED_VIDEOS_PATHS_XML =
    """<paths><files-path name="saved_videos" path="videos/" /></paths>"""
