package com.comst19.dambom.feature.library

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.system.measureNanoTime

class LibraryListCostTest {
    @Volatile private var retained: Any? = null

    @Test
    fun measureSelectionAndListEquality() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("measureListCost") == "true")
        for (size in listOf(100, 1_000, 10_000)) {
            val snapshot = LibrarySnapshot(List(size, ::video))
            val base = toLibraryUiState(snapshot, selectedId = null)
            val equalVideos = base.videos.map { it.copy() }.toPersistentList()
            val selection = LibrarySelectionState(isActive = true, selectedIds = setOf("video-0"))
            measure(
                size,
                "selection",
            ) { base.withSelection(snapshot, selectedId = null, viewMode = LibraryViewMode.GRID, selection = selection) }
            measure(size, "same-reference-equals") { base.videos == base.videos }
            measure(size, "equal-new-list-equals") { base.videos == equalVideos }
            retained = base
        }
    }

    private fun measure(
        size: Int,
        name: String,
        operation: () -> Any,
    ) {
        repeat(500) { retained = operation() }
        val values = List(100) { measureNanoTime { repeat(10) { retained = operation() } } / 10 }.sorted()
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply {
                putString("stream", "LIST_COST size=$size operation=$name medianNs=${values[50]} p90Ns=${values[90]}\n")
            },
        )
    }

    private fun video(index: Int) =
        DownloadTask(
            id = "video-$index",
            url = "https://example.test/$index.mp4",
            sourcePageUrl = "https://example.test/",
            title = "Video $index",
            mimeType = "video/mp4",
            expectedBytes = 100,
            downloadedBytes = 100,
            quality = "original",
            status = DownloadStatus.COMPLETED,
            failureReason = null,
            localFileName = "$index.mp4",
            localFilePath = "/fixture/$index.mp4",
            createdAtMillis = index.toLong(),
            updatedAtMillis = index.toLong(),
        )
}
