package com.comst19.dambom.feature.library.file

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import java.io.File

private val videoFileObservers = LocalVideoFileObservers()

internal fun observeLocalVideoChanges(
    path: String,
    observers: LocalVideoFileObservers = videoFileObservers,
) = callbackFlow {
    val unsubscribe = observers.subscribe(File(path)) { trySend(Unit) }
    trySend(Unit)
    awaitClose { unsubscribe() }
}.conflate().flowOn(Dispatchers.IO)

internal class LocalVideoFileObservers(
    private val createObserver: (File, (String?) -> Unit) -> FileObserver = ::createDirectoryFileObserver,
) {
    private class Watch(
        val observer: FileObserver,
        val listeners: MutableMap<Any, Pair<String, () -> Unit>>,
    )

    private val watches = mutableMapOf<File, Watch>()

    fun subscribe(
        file: File,
        onChange: () -> Unit,
    ): () -> Unit {
        val directory = checkNotNull(file.canonicalFile.parentFile)
        val token = Any()
        synchronized(watches) {
            val watch =
                watches[directory] ?: Watch(
                    createObserver(directory) { path ->
                        val listeners =
                            synchronized(watches) {
                                watches[directory]
                                    ?.listeners
                                    ?.values
                                    ?.filter { isTargetFileEvent(path, it.first) }
                                    ?.map { it.second }
                                    .orEmpty()
                            }
                        listeners.forEach { it() }
                    },
                    mutableMapOf(),
                ).also { watches[directory] = it }
            watch.listeners[token] = file.name to onChange
            if (watch.listeners.size == 1) watch.observer.startWatching()
        }
        return {
            synchronized(watches) {
                watches[directory]?.let { watch ->
                    watch.listeners.remove(token)
                    if (watch.listeners.isEmpty()) {
                        watch.observer.stopWatching()
                        watches.remove(directory)
                    }
                }
            }
        }
    }
}

private const val VIDEO_FILE_OBSERVER_MASK =
    FileObserver.DELETE or
        FileObserver.MOVED_FROM or
        FileObserver.CREATE or
        FileObserver.MOVED_TO or
        FileObserver.CLOSE_WRITE or
        FileObserver.MODIFY or
        FileObserver.ATTRIB

internal fun createDirectoryFileObserver(
    directory: File,
    onChange: (String?) -> Unit,
): FileObserver =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        object : FileObserver(directory, VIDEO_FILE_OBSERVER_MASK) {
            override fun onEvent(
                event: Int,
                path: String?,
            ) {
                onChange(path)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        object : FileObserver(directory.path, VIDEO_FILE_OBSERVER_MASK) {
            override fun onEvent(
                event: Int,
                path: String?,
            ) {
                onChange(path)
            }
        }
    }
