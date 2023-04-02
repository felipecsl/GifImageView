/**
 * Copyright 2014 Google, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.felipecsl.gifimageview.library

import android.util.Log
import com.felipecsl.gifimageview.library.GifHeaderParser
import com.felipecsl.gifimageview.library.GifHeader
import com.felipecsl.gifimageview.library.GifDecoder
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * A class responsible for creating [GifHeader]s from data
 * representing animated gifs.
 */
class GifHeaderParser {
    // Raw data read working array.
    private val block = ByteArray(MAX_BLOCK_SIZE)
    private var rawData: ByteBuffer? = null
    private var header: GifHeader? = null
    private var blockSize = 0
    fun setData(data: ByteBuffer): GifHeaderParser {
        reset()
        rawData = data.asReadOnlyBuffer()
        rawData?.position(0)
        rawData?.order(ByteOrder.LITTLE_ENDIAN)
        return this
    }

    fun setData(data: ByteArray?): GifHeaderParser {
        if (data != null) {
            setData(ByteBuffer.wrap(data))
        } else {
            rawData = null
            header!!.status = GifDecoder.STATUS_OPEN_ERROR
        }
        return this
    }

    fun clear() {
        rawData = null
        header = null
    }

    private fun reset() {
        rawData = null
        Arrays.fill(block, 0.toByte())
        header = GifHeader()
        blockSize = 0
    }

    fun parseHeader(): GifHeader? {
        checkNotNull(rawData) { "You must call setData() before parseHeader()" }
        if (err()) {
            return header
        }
        readHeader()
        if (!err()) {
            readContents()
            if (header!!.numFrames < 0) {
                header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }
        return header
    }/* maxFrames */

    /**
     * Determines if the GIF is animated by trying to read in the first 2 frames
     * This method reparses the data even if the header has already been read.
     */
    val isAnimated: Boolean
        get() {
            readHeader()
            if (!err()) {
                readContents(2 /* maxFrames */)
            }
            return header!!.numFrames > 1
        }
    /**
     * Main file parser. Reads GIF content blocks. Stops after reading maxFrames
     */
    /**
     * Main file parser. Reads GIF content blocks.
     */
    private fun readContents(maxFrames: Int = Int.MAX_VALUE) {
        // Read GIF file content blocks.
        var done = false
        while (!(done || err() || header!!.numFrames > maxFrames)) {
            var code = read()
            when (code) {
                0x2C -> {
                    // The graphics control extension is optional, but will always come first if it exists.
                    // If one did
                    // exist, there will be a non-null current frame which we should use. However if one
                    // did not exist,
                    // the current frame will be null and we must create it here. See issue #134.
                    if (header!!.currentFrame == null) {
                        header!!.currentFrame = GifFrame()
                    }
                    readBitmap()
                }
                0x21 -> {
                    code = read()
                    when (code) {
                        0xf9 -> {
                            // Start a new frame.
                            header!!.currentFrame = GifFrame()
                            readGraphicControlExt()
                        }
                        0xff -> {
                            readBlock()
                            var app = ""
                            var i = 0
                            while (i < 11) {
                                app += block[i].toChar()
                                i++
                            }
                            if (app == "NETSCAPE2.0") {
                                readNetscapeExt()
                            } else {
                                // Don't care.
                                skip()
                            }
                        }
                        0xfe -> skip()
                        0x01 -> skip()
                        else -> skip()
                    }
                }
                0x3b -> done = true
                0x00 -> header!!.status = GifDecoder.STATUS_FORMAT_ERROR
                else -> header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }
    }

    /**
     * Reads Graphics Control Extension values.
     */
    private fun readGraphicControlExt() {
        // Block size.
        read()
        // Packed fields.
        val packed = read()
        // Disposal method.
        header!!.currentFrame!!.dispose = packed and 0x1c shr 2
        if (header!!.currentFrame!!.dispose == 0) {
            // Elect to keep old image if discretionary.
            header!!.currentFrame!!.dispose = 1
        }
        header!!.currentFrame!!.transparency = packed and 1 != 0
        // Delay in milliseconds.
        var delayInHundredthsOfASecond = readShort()
        // TODO: consider allowing -1 to indicate show forever.
        if (delayInHundredthsOfASecond < MIN_FRAME_DELAY) {
            delayInHundredthsOfASecond = DEFAULT_FRAME_DELAY
        }
        header!!.currentFrame!!.delay = delayInHundredthsOfASecond * 10
        // Transparent color index
        header!!.currentFrame!!.transIndex = read()
        // Block terminator
        read()
    }

    /**
     * Reads next frame image.
     */
    private fun readBitmap() {
        // (sub)image position & size.
        header!!.currentFrame!!.ix = readShort()
        header!!.currentFrame!!.iy = readShort()
        header!!.currentFrame!!.iw = readShort()
        header!!.currentFrame!!.ih = readShort()
        val packed = read()
        // 1 - local color table flag interlace
        val lctFlag = packed and 0x80 != 0
        val lctSize = Math.pow(2.0, ((packed and 0x07) + 1).toDouble()).toInt()
        // 3 - sort flag
        // 4-5 - reserved lctSize = 2 << (packed & 7); // 6-8 - local color
        // table size
        header!!.currentFrame!!.interlace = packed and 0x40 != 0
        if (lctFlag) {
            // Read table.
            header!!.currentFrame!!.lct = readColorTable(lctSize)
        } else {
            // No local color table.
            header!!.currentFrame!!.lct = null
        }

        // Save this as the decoding position pointer.
        header!!.currentFrame!!.bufferFrameStart = rawData!!.position()

        // False decode pixel data to advance buffer.
        skipImageData()
        if (err()) {
            return
        }
        header!!.numFrames++
        // Add image to frame.
        header?.currentFrame?.let { header!!.frames.add(it) }
    }

    /**
     * Reads Netscape extension to obtain iteration count.
     */
    private fun readNetscapeExt() {
        do {
            readBlock()
            if (block[0].toInt() == 1) {
                // Loop count sub-block.
                val b1 = block[1].toInt() and 0xff
                val b2 = block[2].toInt() and 0xff
                header?.loopCount = b2 shl 8 or b1
                if (header?.loopCount == 0) {
                    header?.loopCount = GifDecoder.LOOP_FOREVER
                }
            }
        } while (blockSize > 0 && !err())
    }

    /**
     * Reads GIF file header information.
     */
    private fun readHeader() {
        var id = ""
        for (i in 0..5) {
            id += read().toChar()
        }
        if (!id.startsWith("GIF")) {
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            return
        }
        readLSD()
        if (header!!.gctFlag && !err()) {
            header!!.gct = readColorTable(header!!.gctSize)
            header!!.bgColor = header!!.gct!![header!!.bgIndex]
        }
    }

    /**
     * Reads Logical Screen Descriptor.
     */
    private fun readLSD() {
        // Logical screen size.
        header!!.width = readShort()
        header!!.height = readShort()
        // Packed fields
        val packed = read()
        // 1 : global color table flag.
        header!!.gctFlag = packed and 0x80 != 0
        // 2-4 : color resolution.
        // 5 : gct sort flag.
        // 6-8 : gct size.
        header!!.gctSize = 2 shl (packed and 7)
        // Background color index.
        header!!.bgIndex = read()
        // Pixel aspect ratio
        header!!.pixelAspect = read()
    }

    /**
     * Reads color table as 256 RGB integer values.
     *
     * @param ncolors int number of colors to read.
     * @return int array containing 256 colors (packed ARGB with full alpha).
     */
    private fun readColorTable(ncolors: Int): IntArray? {
        val nbytes = 3 * ncolors
        var tab: IntArray? = null
        val c = ByteArray(nbytes)
        try {
            rawData!![c]

            // TODO: what bounds checks are we avoiding if we know the number of colors?
            // Max size to avoid bounds checks.
            tab = IntArray(MAX_BLOCK_SIZE)
            var i = 0
            var j = 0
            while (i < ncolors) {
                val r = c[j++].toInt() and 0xff
                val g = c[j++].toInt() and 0xff
                val b = c[j++].toInt() and 0xff
                tab[i++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        } catch (e: BufferUnderflowException) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Format Error Reading Color Table", e)
            }
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
        }
        return tab
    }

    /**
     * Skips LZW image data for a single frame to advance buffer.
     */
    private fun skipImageData() {
        // lzwMinCodeSize
        read()
        // data sub-blocks
        skip()
    }

    /**
     * Skips variable length blocks up to and including next zero length block.
     */
    private fun skip() {
        try {
            var blockSize: Int
            do {
                blockSize = read()
                rawData!!.position(rawData!!.position() + blockSize)
            } while (blockSize > 0)
        } catch (ex: IllegalArgumentException) {
        }
    }

    /**
     * Reads next variable length block from input.
     *
     * @return number of bytes stored in "buffer"
     */
    private fun readBlock(): Int {
        blockSize = read()
        var n = 0
        if (blockSize > 0) {
            var count = 0
            try {
                while (n < blockSize) {
                    count = blockSize - n
                    rawData!![block, n, count]
                    n += count
                }
            } catch (e: Exception) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                        TAG,
                        "Error Reading Block n: $n count: $count blockSize: $blockSize", e
                    )
                }
                header!!.status = GifDecoder.STATUS_FORMAT_ERROR
            }
        }
        return n
    }

    /**
     * Reads a single byte from the input stream.
     */
    private fun read(): Int {
        var curByte = 0
        try {
            curByte = (rawData!!.get()).toInt() and 0xFF
        } catch (e: Exception) {
            header!!.status = GifDecoder.STATUS_FORMAT_ERROR
        }
        return curByte
    }

    /**
     * Reads next 16-bit value, LSB first.
     */
    private fun readShort(): Int {
        // Read 16-bit value.
        return rawData!!.short.toInt()
    }

    private fun err(): Boolean {
        return header!!.status != GifDecoder.STATUS_OK
    }

    companion object {
        const val TAG = "GifHeaderParser"

        // The minimum frame delay in hundredths of a second.
        const val MIN_FRAME_DELAY = 2

        // The default frame delay in hundredths of a second for GIFs with frame delays less than the
        // minimum.
        const val DEFAULT_FRAME_DELAY = 10
        private const val MAX_BLOCK_SIZE = 256
    }
}