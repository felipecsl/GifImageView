package com.felipecsl.gifimageview.library

import android.graphics.Bitmap
import com.felipecsl.gifimageview.library.GifDecoder.BitmapProvider

internal class SimpleBitmapProvider : BitmapProvider {
    override fun obtain(width: Int, height: Int, config: Bitmap.Config?): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    override fun release(bitmap: Bitmap?) {
        bitmap!!.recycle()
    }

    override fun obtainByteArray(size: Int): ByteArray? {
        return ByteArray(size)
    }

    override fun release(bytes: ByteArray?) {
        // no-op
    }

    override fun obtainIntArray(size: Int): IntArray? {
        return IntArray(size)
    }

    override fun release(array: IntArray?) {
        // no-op
    }
}