package com.felipecsl.gifimageview.app

import android.content.Context
import android.graphics.Bitmap
import androidx.renderscript.Allocation
import androidx.renderscript.Element
import androidx.renderscript.RenderScript
import androidx.renderscript.ScriptIntrinsicBlur

class Blur private constructor(context: Context) {
    private val rs: RenderScript
    private var script: ScriptIntrinsicBlur? = null
    private var input: Allocation? = null
    private var output: Allocation? = null
    private var configured = false
    private var tmp: Bitmap? = null
    private lateinit var pixels: IntArray
    fun blur(image: Bitmap?): Bitmap? {
        var bitmapImage: Bitmap? = image ?: return null
        bitmapImage = bitmapImage?.let { RGB565toARGB888(it) }
        if (!configured) {
            input = Allocation.createFromBitmap(rs, bitmapImage)
            output = Allocation.createTyped(rs, input?.type)
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script?.setRadius(BLUR_RADIUS)
            configured = true
        } else input?.copyFrom(bitmapImage)
        script?.setInput(input)
        script?.forEach(output)
        output?.copyTo(bitmapImage)
        return bitmapImage
    }

    private fun RGB565toARGB888(img: Bitmap): Bitmap? {
        val numPixels = img.width * img.height

        //Create a Bitmap of the appropriate format.
        if (tmp == null) {
            tmp = Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
            pixels = IntArray(numPixels)
        }

        //Get JPEG pixels.  Each int is the color values for one pixel.
        img.getPixels(pixels, 0, img.width, 0, 0, img.width, img.height)

        //Set RGB pixels.
        tmp?.setPixels(pixels, 0, tmp!!.width, 0, 0, tmp!!.width, tmp!!.height)
        return tmp
    }

    companion object {
        private const val BLUR_RADIUS = 25f
        @JvmStatic
        fun newInstance(context: Context): Blur {
            return Blur(context)
        }
    }

    init {
        rs = RenderScript.create(context)
    }
}