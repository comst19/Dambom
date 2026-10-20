package com.comst19.dambom.feature.library.file

import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.io.File

private val videoFileObservers = LocalVideoFileObservers()

internal fun observeLocalVideoChanges(
    path: String,
    observers: LocalVideoFileObservers = videoFileObservers,
) = callbackFlow {
    val unsubscribe = observers.subscribe(File(path)) { trySend(Unit) }
    trySend(Unit)
    awaitClose { unsubscribe() }
}.conflate()

internal class LocalVideoFileObservers(
    private val createObserver: (File, (String?) -> Unit) -> FileObserver = { directory, onChange ->
        @Suppress("DEPRECATION")
        object : FileObserver(
            directory.path,
            DELETE or MOVED_FROM or CREATE or MOVED_TO or CLOSE_WRITE or MODIFY or ATTRIB,
        ) {
            override fun onEvent(
                event: Int,
                path: String?,
            ) {
                onChange(path)
            }
        }
    },
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
