package com.comst19.dambom.feature.library.media

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoThumbnailDecoderTest {
    @Test
    fun `large landscape and portrait JPEGs decode within the thumbnail memory budget`() {
        listOf(3840 to 2160, 2160 to 3840).forEach { (width, height) ->
            withImage(width, height) { file ->
                val result = checkNotNull(decodeVideoThumbnail(file.path))
                try {
                    assertTrue(result.width <= 640)
                    assertTrue(result.height <= 640)
                    assertTrue(result.allocationByteCount <= 640 * 640 * 4)
                    assertTrue(result.allocationByteCount < width * height * 4 / 16)
                } finally {
                    result.recycle()
                }
            }
        }
    }

    @Test
    fun `existing small thumbnails keep their dimensions`() {
        withImage(640, 360) { file ->
            val result = checkNotNull(decodeVideoThumbnail(file.path))
            try {
                assertEquals(640, result.width)
                assertEquals(360, result.height)
            } finally {
                result.recycle()
            }
        }
    }

    @Test
    fun `invalid image returns no thumbnail`() {
        val file = File.createTempFile("invalid-thumbnail", ".jpg")
        try {
            file.writeText("not an image")
            assertNull(decodeVideoThumbnail(file.path))
        } finally {
            file.delete()
        }
    }

    private fun withImage(
        width: Int,
        height: Int,
        test: (File) -> Unit,
    ) {
        val file = File.createTempFile("thumbnail", ".jpg")
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            source.recycle()
            test(file)
        } finally {
            if (!source.isRecycled) source.recycle()
            file.delete()
        }
    }
}
