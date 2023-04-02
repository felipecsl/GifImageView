package com.felipecsl.gifimageview.library

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageView
import java.lang.Exception
import java.lang.IllegalArgumentException

class GifImageView : AppCompatImageView, Runnable {
    private var gifDecoder: GifDecoder? = null
    private var tmpBitmap: Bitmap? = null
    private val myHandler  = Handler(Looper.getMainLooper())
    var isAnimating = false
        private set
    private var renderFrame = false
    private var shouldClear = false
    private var animationThread: Thread? = null
    var onFrameAvailable: OnFrameAvailable? = null

    /**
     * Sets custom display duration in milliseconds for the all frames. Should be called before [ ][.startAnimation]
     *
     * @param framesDisplayDuration Duration in milliseconds. Default value = -1, this property will
     * be ignored and default delay from gif file will be used.
     */
    var framesDisplayDuration = -1L
    var onAnimationStop: OnAnimationStop? = null
    private var animationStartCallback: OnAnimationStart? = null
    private val updateResults = Runnable {
        if (tmpBitmap != null && !tmpBitmap!!.isRecycled) {
            setImageBitmap(tmpBitmap)
        }
    }
    private val cleanupRunnable = Runnable {
        tmpBitmap = null
        gifDecoder = null
        animationThread = null
        shouldClear = false
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
    }

    constructor(context: Context?) : super(context!!) {}

    fun setBytes(bytes: ByteArray?) {
        gifDecoder = GifDecoder()
        try {
            gifDecoder!!.read(bytes)
        } catch (e: Exception) {
            gifDecoder = null
            Log.e(TAG, e.message, e)
            return
        }
        if (isAnimating) {
            startAnimationThread()
        } else {
            gotoFrame(0)
        }
    }

    fun startAnimation() {
        isAnimating = true
        startAnimationThread()
    }

    fun stopAnimation() {
        isAnimating = false
        if (animationThread != null) {
            animationThread!!.interrupt()
            animationThread = null
        }
    }

    fun gotoFrame(frame: Int) {
        if (gifDecoder!!.currentFrameIndex == frame) return
        if (gifDecoder!!.setFrameIndex(frame - 1) && !isAnimating) {
            renderFrame = true
            startAnimationThread()
        }
    }

    fun resetAnimation() {
        gifDecoder!!.resetLoopIndex()
        gotoFrame(0)
    }

    fun clear() {
        isAnimating = false
        renderFrame = false
        shouldClear = true
        stopAnimation()
        myHandler .post(cleanupRunnable)
    }

    private fun canStart(): Boolean {
        return (isAnimating || renderFrame) && gifDecoder != null && animationThread == null
    }

    /**
     * Gets the number of frames read from file.
     *
     * @return frame count.
     */
    val frameCount: Int
        get() = gifDecoder!!.frameCount



     fun getGifWidth(): Int? {
        return gifDecoder?.getWidth()
    }

     fun getGifHeight(): Int? {
        return gifDecoder?.getHeight()
    }

    override fun run() {
        if (animationStartCallback != null) {
            animationStartCallback!!.onAnimationStart()
        }
        do {
            if (!isAnimating && !renderFrame) {
                break
            }
            val advance = gifDecoder!!.advance()

            //milliseconds spent on frame decode
            var frameDecodeTime: Long = 0
            try {
                val before = System.nanoTime()
                tmpBitmap = gifDecoder!!.nextFrame
                if (onFrameAvailable != null) {
                    tmpBitmap = onFrameAvailable!!.onFrameAvailable(tmpBitmap)
                }
                frameDecodeTime = (System.nanoTime() - before) / 1000000
                myHandler .post(updateResults)
            } catch (e: ArrayIndexOutOfBoundsException) {
                Log.w(TAG, e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, e)
            }
            renderFrame = false
            if (!isAnimating || !advance) {
                isAnimating = false
                break
            }
            try {
                var delay = gifDecoder!!.nextDelay.toLong()
                // Sleep for frame duration minus time already spent on frame decode
                // Actually we need next frame decode duration here,
                // but I use previous frame time to make code more readable
                delay -= frameDecodeTime.toLong()
                if (delay > 0) {
                    Thread.sleep((if (framesDisplayDuration > 0) framesDisplayDuration else delay))
                }
            } catch (e: InterruptedException) {
                // suppress exception
            }
        } while (isAnimating)
        if (shouldClear) {
            myHandler.post(cleanupRunnable)
        }
        animationThread = null
        if (onAnimationStop != null) {
            onAnimationStop!!.onAnimationStop()
        }
    }

    interface OnFrameAvailable {
        fun onFrameAvailable(bitmap: Bitmap?): Bitmap?
    }

    fun setOnAnimationStart(animationStart: OnAnimationStart?) {
        animationStartCallback = animationStart
    }

    interface OnAnimationStop {
        fun onAnimationStop()
    }

    interface OnAnimationStart {
        fun onAnimationStart()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clear()
    }

    private fun startAnimationThread() {
        if (canStart()) {
            animationThread = Thread(this)
            animationThread!!.start()
        }
    }

    companion object {
        private const val TAG = "GifDecoderView"
    }
}