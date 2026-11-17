@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.FrameworkMuxer
import java.nio.ByteBuffer

internal class StreamMp4MuxerFactory private constructor(
    private val delegate: FrameworkMuxer.Factory,
) : Muxer.Factory by delegate {
    constructor() : this(FrameworkMuxer.Factory())

    override fun create(path: String): Muxer {
        val muxer = delegate.create(path)
        return object : Muxer by muxer {
            private var avcTrackId: Int? = null

            override fun addTrack(format: Format): Int {
                if (format.sampleMimeType != MimeTypes.VIDEO_H264) return muxer.addTrack(format)

                // The TS extractor emits three-byte AVC start codes. MediaMuxer requires four in CSD.
                val initializationData =
                    format.initializationData.map { nal ->
                        if (nal.size <= AVC_SHORT_START_CODE_SIZE) return@map nal
                        if (nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()) {
                            byteArrayOf(0) + nal
                        } else {
                            nal
                        }
                    }
                return muxer.addTrack(format.buildUpon().setInitializationData(initializationData).build()).also {
                    avcTrackId = it
                }
            }

            override fun writeSampleData(
                trackId: Int,
                data: ByteBuffer,
                bufferInfo: BufferInfo,
            ) {
                val normalized = if (trackId == avcTrackId) normalizeAvcSample(data, bufferInfo.size) else null
                if (normalized == null) {
                    muxer.writeSampleData(trackId, data, bufferInfo)
                    return
                }
                muxer.writeSampleData(
                    trackId,
                    normalized,
                    BufferInfo(bufferInfo.presentationTimeUs, normalized.remaining(), bufferInfo.flags),
                )
            }
        }
    }
}

private const val AVC_SHORT_START_CODE_SIZE = 3

internal fun normalizeAvcSample(
    data: ByteBuffer,
    size: Int,
): ByteBuffer? {
    val start = data.position()
    val end = start + size
    var shortPrefixes: MutableList<Int>? = null
    var index = start
    // MediaMuxer subtracts four bytes between NAL units. A short prefix would truncate the previous NAL.
    while (index < end - AVC_SHORT_START_CODE_SIZE) {
        if (data.get(index) == 0.toByte() && data.get(index + 1) == 0.toByte() && data.get(index + 2) == 1.toByte()) {
            if (index == start || data.get(index - 1) != 0.toByte()) {
                val prefixes = shortPrefixes ?: mutableListOf<Int>().also { shortPrefixes = it }
                prefixes.add(index)
            }
            index += AVC_SHORT_START_CODE_SIZE
        } else {
            index++
        }
    }

    val prefixes = shortPrefixes ?: return null
    val input = data.duplicate()
    val output = ByteBuffer.allocate(size + prefixes.size)
    for (prefix in prefixes) {
        input.limit(prefix)
        output.put(input)
        output.put(0)
    }
    input.limit(end)
    output.put(input)
    output.flip()
    return output
}
