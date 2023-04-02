/**
 * Copyright (c) 2013 Xcellent Creations, Inc.
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

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Reads frame data from a GIF image source and decodes it into individual frames
 * for animation purposes. Image data can be read from either and InputStream source
 * or a byte[].
 *
 * This class is optimized for running animations with the frames, there
 * are no methods to get individual frame images, only to decode the next frame in the
 * animation sequence. Instead, it lowers its memory footprint by only housing the minimum
 * data necessary to decode the next frame in the animation sequence.
 *
 * The animation must be manually moved forward using [.advance] before requesting the next
 * frame. This method must also be called before you request the first frame or an error will
 * occur.
 *
 * Implementation adapted from sample code published in Lyons. (2004). *Java for Programmers*,
 * republished under the MIT Open Source License
 */
internal class GifDecoder @JvmOverloads constructor(private val bitmapProvider: BitmapProvider = SimpleBitmapProvider()) {
    // Global File Header values and parsing flags.
    // Active color table.
    private var act: IntArray? = null

    // Private color table that can be modified if needed.
    private val pct = IntArray(256)

    // Raw GIF data from input source.
    var data: ByteBuffer? = null
        private set

    // Raw data read working array.
    private var block: ByteArray? = null
    private var workBuffer: ByteArray? = null
    private var workBufferSize = 0
    private var workBufferPosition = 0
    private var parser: GifHeaderParser? = null

    // LZW decoder working arrays.
    private var prefix: ShortArray? = null
    private var suffix: ByteArray? = null
    private var pixelStack: ByteArray? = null
    private var mainPixels: ByteArray? = null
    private var mainScratch: IntArray? = null

    /**
     * Gets the current index of the animation frame, or -1 if animation hasn't not yet started.
     *
     * @return frame index.
     */
    var currentFrameIndex = 0
        private set

    /**
     * Gets the number of loops that have been shown.
     *
     * @return iteration count.
     */
    var loopIndex = 0
        private set
    private var header: GifHeader?
    private var previousImage: Bitmap? = null
    private var savePrevious = false

    /**
     * Returns the current status of the decoder.
     *
     *
     *  Status will update per frame to allow the caller to tell whether or not the current frame
     * was decoded successfully and/or completely. Format and open failures persist across frames.
     *
     */
    var status = 0
        private set
    private var sampleSize = 0
    private var downsampledHeight = 0
    private var downsampledWidth = 0
    private var isFirstFrameTransparent = false

    /**
     * An interface that can be used to provide reused [android.graphics.Bitmap]s to avoid GCs
     * from constantly allocating [android.graphics.Bitmap]s for every frame.
     */
    internal interface BitmapProvider {
        /**
         * Returns an [Bitmap] with exactly the given dimensions and config.
         *
         * @param width  The width in pixels of the desired [android.graphics.Bitmap].
         * @param height The height in pixels of the desired [android.graphics.Bitmap].
         * @param config The [android.graphics.Bitmap.Config] of the desired [               ].
         */
        fun obtain(width: Int, height: Int, config: Bitmap.Config?): Bitmap

        /**
         * Releases the given Bitmap back to the pool.
         */
        fun release(bitmap: Bitmap?)

        /**
         * Returns a byte array used for decoding and generating the frame bitmap.
         *
         * @param size the size of the byte array to obtain
         */
        fun obtainByteArray(size: Int): ByteArray?

        /**
         * Releases the given byte array back to the pool.
         */
        fun release(bytes: ByteArray?)

        /**
         * Returns an int array used for decoding/generating the frame bitmaps.
         * @param size
         */
        fun obtainIntArray(size: Int): IntArray?

        /**
         * Release the given array back to the pool.
         * @param array
         */
        fun release(array: IntArray?)
    }

    @JvmOverloads
    constructor(
        provider: BitmapProvider, gifHeader: GifHeader?, rawData: ByteBuffer,
        sampleSize: Int = 1 /*sampleSize*/
    ) : this(provider) {
        setData(gifHeader, rawData, sampleSize)
    }


    fun getWidth(): Int? {
        return header?.width
    }

    fun getHeight(): Int? {
        return header?.height
    }


    /**
     * Move the animation frame counter forward.
     *
     * @return boolean specifying if animation should continue or if loopCount has been fulfilled.
     */
    fun advance(): Boolean {
        if (header!!.numFrames <= 0) {
            return false
        }
        if (currentFrameIndex == frameCount - 1) {
            loopIndex++
        }
        if (header!!.loopCount != LOOP_FOREVER && loopIndex > header!!.loopCount) {
            return false
        }
        currentFrameIndex = (currentFrameIndex + 1) % header!!.numFrames
        return true
    }

    /**
     * Gets display duration for specified frame.
     *
     * @param n int index of frame.
     * @return delay in milliseconds.
     */
    fun getDelay(n: Int): Int {
        var delay = -1
        if (n >= 0 && n < header!!.numFrames) {
            delay = header!!.frames[n].delay
        }
        return delay
    }

    /**
     * Gets display duration for the upcoming frame in ms.
     */
    val nextDelay: Int
        get() = if (header!!.numFrames <= 0 || currentFrameIndex < 0) {
            0
        } else getDelay(currentFrameIndex)

    /**
     * Gets the number of frames read from file.
     *
     * @return frame count.
     */
    val frameCount: Int
        get() = header!!.numFrames

    /**
     * Sets the frame pointer to a specific frame
     *
     * @return boolean true if the move was successful
     */
    fun setFrameIndex(frame: Int): Boolean {
        if (frame < INITIAL_FRAME_POINTER || frame >= frameCount) {
            return false
        }
        currentFrameIndex = frame
        return true
    }

    /**
     * Resets the frame pointer to before the 0th frame, as if we'd never used this decoder to
     * decode any frames.
     */
    fun resetFrameIndex() {
        currentFrameIndex = INITIAL_FRAME_POINTER
    }

    /**
     * Resets the loop index to the first loop.
     */
    fun resetLoopIndex() {
        loopIndex = 0
    }

    /**
     * Gets the "Netscape" iteration count, if any. A count of 0 means repeat indefinitely.
     *
     * @return iteration count if one was specified, else 1.
     */
    val loopCount: Int
        get() = header!!.loopCount

    /**
     * Returns an estimated byte size for this decoder based on the data provided to [ ][.setData], as well as internal buffers.
     */
    val byteSize: Int
        get() = data!!.limit() + mainPixels!!.size + mainScratch!!.size * BYTES_PER_INTEGER// Prepare local copy of color table ("pct = act"), see #1068
    // Forget about act reference from shared header object, use copied version
    // Set transparent color if specified.

    // Transfer pixel data to image.
// No color table defined.

    // Reset the transparent pixel in the color table
// Set the appropriate color table.
    /**
     * Get the next frame in the animation sequence.
     *
     * @return Bitmap representation of frame.
     */
    @get:Synchronized
    val nextFrame: Bitmap?
        get() {
            if (header!!.numFrames <= 0 || currentFrameIndex < 0) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                        TAG,
                        "unable to decode frame, frameCount=" + header!!.numFrames + " framePointer="
                                + currentFrameIndex
                    )
                }
                status = STATUS_FORMAT_ERROR
            }
            if (status == STATUS_FORMAT_ERROR || status == STATUS_OPEN_ERROR) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Unable to decode frame, status=$status")
                }
                return null
            }
            status = STATUS_OK
            val currentFrame = header!!.frames[currentFrameIndex]
            var previousFrame: GifFrame? = null
            val previousIndex = currentFrameIndex - 1
            if (previousIndex >= 0) {
                previousFrame = header!!.frames[previousIndex]
            }

            // Set the appropriate color table.
            act = if (currentFrame.lct != null) currentFrame.lct else header!!.gct
            if (act == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "No Valid Color Table for frame #$currentFrameIndex")
                }
                // No color table defined.
                status = STATUS_FORMAT_ERROR
                return null
            }

            // Reset the transparent pixel in the color table
            if (currentFrame.transparency) {
                // Prepare local copy of color table ("pct = act"), see #1068
                System.arraycopy(act, 0, pct, 0, act!!.size)
                // Forget about act reference from shared header object, use copied version
                act = pct
                // Set transparent color if specified.
                act!![currentFrame.transIndex] = 0
            }

            // Transfer pixel data to image.
            return setPixels(currentFrame, previousFrame)
        }

    /**
     * Reads GIF image from stream.
     *
     * @param is containing GIF file.
     * @return read status code (0 = no errors).
     */
    fun read(`is`: InputStream?, contentLength: Int): Int {
        if (`is` != null) {
            try {
                val capacity = if (contentLength > 0) contentLength + 4096 else 16384
                val buffer = ByteArrayOutputStream(capacity)
                var nRead: Int
                val data = ByteArray(16384)
                while (`is`.read(data, 0, data.size).also { nRead = it } != -1) {
                    buffer.write(data, 0, nRead)
                }
                buffer.flush()
                read(buffer.toByteArray())
            } catch (e: IOException) {
                Log.w(TAG, "Error reading data from stream", e)
            }
        } else {
            status = STATUS_OPEN_ERROR
        }
        try {
            `is`?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing stream", e)
        }
        return status
    }

    fun clear() {
        header = null
        if (mainPixels != null) {
            bitmapProvider.release(mainPixels)
        }
        if (mainScratch != null) {
            bitmapProvider.release(mainScratch)
        }
        if (previousImage != null) {
            bitmapProvider.release(previousImage)
        }
        previousImage = null
        data = null
        isFirstFrameTransparent = false
        if (block != null) {
            bitmapProvider.release(block)
        }
        if (workBuffer != null) {
            bitmapProvider.release(workBuffer)
        }
    }

    @Synchronized
    fun setData(header: GifHeader?, data: ByteArray?) {
        setData(header, ByteBuffer.wrap(data))
    }

    @Synchronized
    fun setData(header: GifHeader?, buffer: ByteBuffer) {
        setData(header, buffer, 1)
    }

    @Synchronized
    fun setData(header: GifHeader?, buffer: ByteBuffer, sampleSize: Int) {
        var sampleSize = sampleSize
        require(sampleSize > 0) { "Sample size must be >=0, not: $sampleSize" }
        // Make sure sample size is a power of 2.
        sampleSize = Integer.highestOneBit(sampleSize)
        status = STATUS_OK
        this.header = header
        isFirstFrameTransparent = false
        currentFrameIndex = INITIAL_FRAME_POINTER
        resetLoopIndex()
        // Initialize the raw data buffer.
        data = buffer.asReadOnlyBuffer()
        data?.position(0)
        data?.order(ByteOrder.LITTLE_ENDIAN)

        // No point in specially saving an old frame if we're never going to use it.
        savePrevious = false
        for (frame in header!!.frames) {
            if (frame.dispose == DISPOSAL_PREVIOUS) {
                savePrevious = true
                break
            }
        }
        this.sampleSize = sampleSize
        downsampledWidth = header.width / sampleSize
        downsampledHeight = header.height / sampleSize
        // Now that we know the size, init scratch arrays.
        // TODO Find a way to avoid this entirely or at least downsample it (either should be possible).
        mainPixels = bitmapProvider.obtainByteArray(header.width * header.height)
        mainScratch = bitmapProvider.obtainIntArray(downsampledWidth * downsampledHeight)
    }

    private val headerParser: GifHeaderParser
        private get() {
            if (parser == null) {
                parser = GifHeaderParser()
            }
            return parser!!
        }

    /**
     * Reads GIF image from byte array.
     *
     * @param data containing GIF file.
     * @return read status code (0 = no errors).
     */
    @Synchronized
    fun read(data: ByteArray?): Int {
        header = headerParser.setData(data).parseHeader()
        data?.let { setData(header, it) }
        return status
    }

    /**
     * Creates new frame image from current data (and previous frames as specified by their
     * disposition codes).
     */
    private fun setPixels(currentFrame: GifFrame, previousFrame: GifFrame?): Bitmap {
        // Final location of blended pixels.
        val dest = mainScratch

        // clear all pixels when meet first frame
        if (previousFrame == null) {
            Arrays.fill(dest, 0)
        }

        // fill in starting image contents based on last image's dispose code
        if (previousFrame != null && previousFrame.dispose > DISPOSAL_UNSPECIFIED) {
            // We don't need to do anything for DISPOSAL_NONE, if it has the correct pixels so will our
            // mainScratch and therefore so will our dest array.
            if (previousFrame.dispose == DISPOSAL_BACKGROUND) {
                // Start with a canvas filled with the background color
                var c = 0
                if (!currentFrame.transparency) {
                    c = header!!.bgColor
                    if (currentFrame.lct != null && header!!.bgIndex == currentFrame.transIndex) {
                        c = 0
                    }
                } else if (currentFrameIndex == 0) {
                    // TODO: We should check and see if all individual pixels are replaced. If they are, the
                    // first frame isn't actually transparent. For now, it's simpler and safer to assume
                    // drawing a transparent background means the GIF contains transparency.
                    isFirstFrameTransparent = true
                }
                fillRect(dest, previousFrame, c)
            } else if (previousFrame.dispose == DISPOSAL_PREVIOUS) {
                if (previousImage == null) {
                    fillRect(dest, previousFrame, 0)
                } else {
                    // Start with the previous frame
                    val downsampledIH = previousFrame.ih / sampleSize
                    val downsampledIY = previousFrame.iy / sampleSize
                    val downsampledIW = previousFrame.iw / sampleSize
                    val downsampledIX = previousFrame.ix / sampleSize
                    val topLeft = downsampledIY * downsampledWidth + downsampledIX
                    previousImage!!.getPixels(
                        dest, topLeft, downsampledWidth,
                        downsampledIX, downsampledIY, downsampledIW, downsampledIH
                    )
                }
            }
        }

        // Decode pixels for this frame into the global pixels[] scratch.
        decodeBitmapData(currentFrame)
        val downsampledIH = currentFrame.ih / sampleSize
        val downsampledIY = currentFrame.iy / sampleSize
        val downsampledIW = currentFrame.iw / sampleSize
        val downsampledIX = currentFrame.ix / sampleSize
        // Copy each source line to the appropriate place in the destination.
        var pass = 1
        var inc = 8
        var iline = 0
        val isFirstFrame = currentFrameIndex == 0
        for (i in 0 until downsampledIH) {
            var line = i
            if (currentFrame.interlace) {
                if (iline >= downsampledIH) {
                    pass++
                    when (pass) {
                        2 -> iline = 4
                        3 -> {
                            iline = 2
                            inc = 4
                        }
                        4 -> {
                            iline = 1
                            inc = 2
                        }
                        else -> {}
                    }
                }
                line = iline
                iline += inc
            }
            line += downsampledIY
            if (line < downsampledHeight) {
                val k = line * downsampledWidth
                // Start of line in dest.
                var dx = k + downsampledIX
                // End of dest line.
                var dlim = dx + downsampledIW
                if (k + downsampledWidth < dlim) {
                    // Past dest edge.
                    dlim = k + downsampledWidth
                }
                // Start of line in source.
                var sx = i * sampleSize * currentFrame.iw
                val maxPositionInSource = sx + (dlim - dx) * sampleSize
                while (dx < dlim) {
                    // Map color and insert in destination.
                    var averageColor: Int
                    averageColor = if (sampleSize == 1) {
                        val currentColorIndex = mainPixels!![sx].toInt() and 0x000000ff
                        act!![currentColorIndex]
                    } else {
                        // TODO: This is substantially slower (up to 50ms per frame) than just grabbing the
                        // current color index above, even with a sample size of 1.
                        averageColorsNear(sx, maxPositionInSource, currentFrame.iw)
                    }
                    if (averageColor != 0) {
                        dest!![dx] = averageColor
                    } else if (!isFirstFrameTransparent && isFirstFrame) {
                        isFirstFrameTransparent = true
                    }
                    sx += sampleSize
                    dx++
                }
            }
        }

        // Copy pixels into previous image
        if (savePrevious && (currentFrame.dispose == DISPOSAL_UNSPECIFIED
                    || currentFrame.dispose == DISPOSAL_NONE)
        ) {
            if (previousImage == null) {
                previousImage = nextBitmap
            }
            previousImage!!.setPixels(
                dest, 0, downsampledWidth, 0, 0, downsampledWidth,
                downsampledHeight
            )
        }

        // Set pixels for current image.
        val result = nextBitmap
        result.setPixels(dest, 0, downsampledWidth, 0, 0, downsampledWidth, downsampledHeight)
        return result
    }

    private fun fillRect(dest: IntArray?, frame: GifFrame, bgColor: Int) {
        // The area used by the graphic must be restored to the background color.
        val downsampledIH = frame.ih / sampleSize
        val downsampledIY = frame.iy / sampleSize
        val downsampledIW = frame.iw / sampleSize
        val downsampledIX = frame.ix / sampleSize
        val topLeft = downsampledIY * downsampledWidth + downsampledIX
        val bottomLeft = topLeft + downsampledIH * downsampledWidth
        var left = topLeft
        while (left < bottomLeft) {
            val right = left + downsampledIW
            for (pointer in left until right) {
                dest!![pointer] = bgColor
            }
            left += downsampledWidth
        }
    }

    private fun averageColorsNear(
        positionInMainPixels: Int, maxPositionInMainPixels: Int,
        currentFrameIw: Int
    ): Int {
        var alphaSum = 0
        var redSum = 0
        var greenSum = 0
        var blueSum = 0
        var totalAdded = 0
        // Find the pixels in the current row.
        run {
            var i = positionInMainPixels
            while (i < positionInMainPixels + sampleSize && i < mainPixels!!.size && i < maxPositionInMainPixels) {
                val currentColorIndex = mainPixels!![i].toInt() and 0xff
                val currentColor = act!![currentColorIndex]
                if (currentColor != 0) {
                    alphaSum += currentColor shr 24 and 0x000000ff
                    redSum += currentColor shr 16 and 0x000000ff
                    greenSum += currentColor shr 8 and 0x000000ff
                    blueSum += currentColor and 0x000000ff
                    totalAdded++
                }
                i++
            }
        }
        // Find the pixels in the next row.
        var i = positionInMainPixels + currentFrameIw
        while (i < positionInMainPixels + currentFrameIw + sampleSize && i < mainPixels!!.size && i < maxPositionInMainPixels) {
            val currentColorIndex = mainPixels!![i].toInt() and 0xff
            val currentColor = act!![currentColorIndex]
            if (currentColor != 0) {
                alphaSum += currentColor shr 24 and 0x000000ff
                redSum += currentColor shr 16 and 0x000000ff
                greenSum += currentColor shr 8 and 0x000000ff
                blueSum += currentColor and 0x000000ff
                totalAdded++
            }
            i++
        }
        return if (totalAdded == 0) {
            0
        } else {
            (alphaSum / totalAdded shl 24
                    or (redSum / totalAdded shl 16)
                    or (greenSum / totalAdded shl 8)
                    or blueSum / totalAdded)
        }
    }

    /**
     * Decodes LZW image data into pixel array. Adapted from John Cristy's BitmapMagick.
     */
    private fun decodeBitmapData(frame: GifFrame?) {
        workBufferSize = 0
        workBufferPosition = 0
        if (frame != null) {
            // Jump to the frame start position.
            data!!.position(frame.bufferFrameStart)
        }
        val npix = if (frame == null) header!!.width * header!!.height else frame.iw * frame.ih
        var available: Int
        val clear: Int
        var codeMask: Int
        var codeSize: Int
        val endOfInformation: Int
        var inCode: Int
        var oldCode: Int
        var bits: Int
        var code: Int
        var count: Int
        var i: Int
        var datum: Int
        val dataSize: Int
        var first: Int
        var top: Int
        var bi: Int
        var pi: Int
        if (mainPixels == null || mainPixels!!.size < npix) {
            // Allocate new pixel array.
            mainPixels = bitmapProvider.obtainByteArray(npix)
        }
        if (prefix == null) {
            prefix = ShortArray(MAX_STACK_SIZE)
        }
        if (suffix == null) {
            suffix = ByteArray(MAX_STACK_SIZE)
        }
        if (pixelStack == null) {
            pixelStack = ByteArray(MAX_STACK_SIZE + 1)
        }

        // Initialize GIF data stream decoder.
        dataSize = readByte()
        clear = 1 shl dataSize
        endOfInformation = clear + 1
        available = clear + 2
        oldCode = NULL_CODE
        codeSize = dataSize + 1
        codeMask = (1 shl codeSize) - 1
        code = 0
        while (code < clear) {

            // XXX ArrayIndexOutOfBoundsException.
            prefix!![code] = 0
            suffix!![code] = code.toByte()
            code++
        }

        // Decode GIF pixel stream.
        bi = 0
        pi = bi
        top = pi
        first = top
        count = first
        bits = count
        datum = bits
        i = 0
        while (i < npix) {

            // Load bytes until there are enough bits for a code.
            if (count == 0) {
                // Read a new data block.
                count = readBlock()
                if (count <= 0) {
                    status = STATUS_PARTIAL_DECODE
                    break
                }
                bi = 0
            }
            datum += block!![bi].toInt() and 0xff shl bits
            bits += 8
            bi++
            count--
            while (bits >= codeSize) {
                // Get the next code.
                code = datum and codeMask
                datum = datum shr codeSize
                bits -= codeSize

                // Interpret the code.
                if (code == clear) {
                    // Reset decoder.
                    codeSize = dataSize + 1
                    codeMask = (1 shl codeSize) - 1
                    available = clear + 2
                    oldCode = NULL_CODE
                    continue
                }
                if (code > available) {
                    status = STATUS_PARTIAL_DECODE
                    break
                }
                if (code == endOfInformation) {
                    break
                }
                if (oldCode == NULL_CODE) {
                    pixelStack!![top++] = suffix!![code]
                    oldCode = code
                    first = code
                    continue
                }
                inCode = code
                if (code >= available) {
                    pixelStack!![top++] = first.toByte()
                    code = oldCode
                }
                while (code >= clear) {
                    pixelStack!![top++] = suffix!![code]
                    code = prefix!![code].toInt()
                }
                first = suffix!![code].toInt() and 0xff
                pixelStack!![top++] = first.toByte()

                // Add a new string to the string table.
                if (available < MAX_STACK_SIZE) {
                    prefix!![available] = oldCode.toShort()
                    suffix!![available] = first.toByte()
                    available++
                    if (available and codeMask == 0 && available < MAX_STACK_SIZE) {
                        codeSize++
                        codeMask += available
                    }
                }
                oldCode = inCode
                while (top > 0) {
                    // Pop a pixel off the pixel stack.
                    mainPixels!![pi++] = pixelStack!![--top]
                    i++
                }
            }
        }

        // Clear missing pixels.
        i = pi
        while (i < npix) {
            mainPixels!![i] = 0
            i++
        }
    }

    /**
     * Reads the next chunk for the intermediate work buffer.
     */
    private fun readChunkIfNeeded() {
        if (workBufferSize > workBufferPosition) {
            return
        }
        if (workBuffer == null) {
            workBuffer = bitmapProvider.obtainByteArray(WORK_BUFFER_SIZE) //?: ByteArray(WORK_BUFFER_SIZE)
        }
        workBufferPosition = 0
        workBufferSize = Math.min(data!!.remaining(), WORK_BUFFER_SIZE)
        data!![workBuffer, 0, workBufferSize]
    }

    /**
     * Reads a single byte from the input stream.
     */
    private fun readByte(): Int {
        return try {
            readChunkIfNeeded()
            workBuffer!![workBufferPosition++].toInt() and 0xFF
           // ByteArray(10)[3]and 0xFF
        } catch (e: Exception) {
            status = STATUS_FORMAT_ERROR
            0
        }
    }

    /**
     * Reads next variable length block from input.
     *
     * @return number of bytes stored in "buffer".
     */
    private fun readBlock(): Int {
        val blockSize = readByte()
        if (blockSize > 0) {
            try {
                if (block == null) {
                    block = bitmapProvider.obtainByteArray(255)
                }
                val remaining = workBufferSize - workBufferPosition
                if (remaining >= blockSize) {
                    // Block can be read from the current work buffer.
                    System.arraycopy(workBuffer, workBufferPosition, block, 0, blockSize)
                    workBufferPosition += blockSize
                } else if (data!!.remaining() + remaining >= blockSize) {
                    // Block can be read in two passes.
                    System.arraycopy(workBuffer, workBufferPosition, block, 0, remaining)
                    workBufferPosition = workBufferSize
                    readChunkIfNeeded()
                    val secondHalfRemaining = blockSize - remaining
                    System.arraycopy(workBuffer, 0, block, remaining, secondHalfRemaining)
                    workBufferPosition += secondHalfRemaining
                } else {
                    status = STATUS_FORMAT_ERROR
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error Reading Block", e)
                status = STATUS_FORMAT_ERROR
            }
        }
        return blockSize
    }

    private val nextBitmap: Bitmap
        private get() {
            val config =
                if (isFirstFrameTransparent) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            val result = bitmapProvider.obtain(downsampledWidth, downsampledHeight, config)
            setAlpha(result)
            return result
        }

    companion object {
        private val TAG = GifDecoder::class.java.simpleName

        /**
         * File read status: No errors.
         */
        const val STATUS_OK = 0

        /**
         * File read status: Error decoding file (may be partially decoded).
         */
        const val STATUS_FORMAT_ERROR = 1

        /**
         * File read status: Unable to open source.
         */
        const val STATUS_OPEN_ERROR = 2

        /**
         * Unable to fully decode the current frame.
         */
        const val STATUS_PARTIAL_DECODE = 3

        /**
         * max decoder pixel stack size.
         */
        private const val MAX_STACK_SIZE = 4096

        /**
         * GIF Disposal Method meaning take no action.
         */
        private const val DISPOSAL_UNSPECIFIED = 0

        /**
         * GIF Disposal Method meaning leave canvas from previous frame.
         */
        private const val DISPOSAL_NONE = 1

        /**
         * GIF Disposal Method meaning clear canvas to background color.
         */
        private const val DISPOSAL_BACKGROUND = 2

        /**
         * GIF Disposal Method meaning clear canvas to frame before last.
         */
        private const val DISPOSAL_PREVIOUS = 3
        private const val NULL_CODE = -1
        private const val INITIAL_FRAME_POINTER = -1
        const val LOOP_FOREVER = -1
        private const val BYTES_PER_INTEGER = 4

        // Temporary buffer for block reading. Reads 16k chunks from the native buffer for processing,
        // to greatly reduce JNI overhead.
        private const val WORK_BUFFER_SIZE = 16384
        @TargetApi(12)
        private fun setAlpha(bitmap: Bitmap) {
            if (Build.VERSION.SDK_INT >= 12) {
                bitmap.setHasAlpha(true)
            }
        }
    }

    init {
        header = GifHeader()
    }
}