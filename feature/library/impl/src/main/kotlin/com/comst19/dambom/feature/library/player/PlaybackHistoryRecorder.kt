package com.comst19.dambom.feature.library.player

import androidx.media3.common.Player
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlaybackHistoryRecorder(
    private val repository: PlaybackHistoryRepository,
    private val scope: CoroutineScope,
    private val onFailure: suspend () -> Unit,
) {
    private val writes = Mutex()
    private var playedId: String? = null
    private var lastPlayedAt = 0L
    private var lastSaved: PlaybackHistory? = null

    fun onPlaying(player: Player) {
        playedId = player.currentMediaItem?.mediaId
        lastPlayedAt = System.currentTimeMillis()
        save(player)
    }

    suspend fun awaitPendingWrites() = writes.withLock {}

    fun reset() {
        playedId = null
    }

    fun save(player: Player) {
        val id = player.currentMediaItem?.mediaId ?: return
        if (playedId != id || player.duration <= 0L) return
        val history =
            PlaybackHistory(
                id,
                player.currentPosition.coerceIn(0L, player.duration),
                player.duration,
                lastPlayedAt,
            )
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            writes.withLock {
                if (history == lastSaved) return@withLock
                suspendRunCatching {
                    repository.saveProgress(
                        id,
                        history.positionMillis,
                        history.durationMillis,
                        history.lastPlayedAtMillis,
                    )
                }.onSuccess { lastSaved = history }.onFailure { onFailure() }
            }
        }
    }
}

internal fun resumePosition(history: PlaybackHistory?): Long {
    if (history == null || history.durationMillis <= 0L) return 0L
    val position = history.positionMillis.coerceIn(0L, history.durationMillis)
    return if (history.durationMillis - position <= COMPLETION_THRESHOLD_MILLIS) 0L else position
}

private const val COMPLETION_THRESHOLD_MILLIS = 1_000L
