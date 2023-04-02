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


import kotlin.collections.ArrayList

/**
 * A header object containing the number of frames in an animated GIF image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the GIF. Can
 * be shared by one or more [GifDecoder]s to play the same animated GIF in multiple views.
 */
class GifHeader {
    @JvmField
    var gct: IntArray? = null

    /**
     * Global status code of GIF data parsing.
     */
    @JvmField
    var status = GifDecoder.STATUS_OK
    var numFrames = 0
    @JvmField
    var currentFrame: GifFrame? = null
    @JvmField
    var frames: ArrayList<GifFrame> = ArrayList()

    // Logical screen size.
    // Full image width.
    @JvmField
    var width = 0

    // Full image height.
    @JvmField
    var height = 0

    // 1 : global color table flag.
    @JvmField
    var gctFlag = false

    // 2-4 : color resolution.
    // 5 : gct sort flag.
    // 6-8 : gct size.
    @JvmField
    var gctSize = 0

    // Background color index.
    @JvmField
    var bgIndex = 0

    // Pixel aspect ratio.
    @JvmField
    var pixelAspect = 0

    //TODO: this is set both during reading the header and while decoding frames...
    @JvmField
    var bgColor = 0
    @JvmField
    var loopCount = 0
}