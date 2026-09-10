package com.comst19.dambom.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class LibraryStateScaleTest {
    @Test
    fun `prepared library retains correct results at increasing sizes`() {
        for (size in listOf(100, 1_000, 10_000)) {
            val videos = List(size) { savedVideo("video$it").copy(updatedAtMillis = it.toLong()) }
            val snapshot = LibrarySnapshot(videos)
            val expectedCount = videos.count { it.title.contains("video9") }
            repeat(10) {
                toLibraryUiState(LibrarySnapshot(videos), selectedId = null, query = "video9")
                toLibraryUiState(snapshot, selectedId = null, query = "video9")
            }
            val rebuilding = mutableListOf<Long>()
            val reusing = mutableListOf<Long>()
            repeat(30) { iteration ->
                fun rebuild() {
                    rebuilding +=
                        measureNanoTime {
                            val state = toLibraryUiState(LibrarySnapshot(videos), selectedId = null, query = "video9")
                            assertEquals(expectedCount, state.videos.size)
                            assertEquals(size * 100L, state.totalBytes)
                        }
                }

                fun reuse() {
                    reusing +=
                        measureNanoTime {
                            val state = toLibraryUiState(snapshot, selectedId = null, query = "video9")
                            assertEquals(expectedCount, state.videos.size)
                            assertEquals(size * 100L, state.totalBytes)
                        }
                }
                if (iteration % 2 == 0) {
                    rebuild()
                    reuse()
                } else {
                    reuse()
                    rebuild()
                }
            }
            println(
                "library-size=$size rebuild-median-ns=${rebuilding.sorted()[rebuilding.size / 2]} " +
                    "reuse-median-ns=${reusing.sorted()[reusing.size / 2]}",
            )
        }
    }
}
