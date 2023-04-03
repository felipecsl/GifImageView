package com.felipecsl.gifimageview.app

import android.util.Log

object GifDataDownloader {
    private const val TAG = "GifDataDownloader"
    @JvmStatic
    fun downloadGifData(gifUrl: String?, callback: GifDataDownloaderCallback?) {
        if (gifUrl == null) return
        Thread {
            try {
                val gifData = ByteArrayHttpClient.get(gifUrl)
                callback?.onGifDownloaded(gifData)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "GifDecode OOM: $gifUrl", e)
            }
        }.start()
    }

    interface GifDataDownloaderCallback {
        fun onGifDownloaded(gifData: ByteArray?)
    }
}