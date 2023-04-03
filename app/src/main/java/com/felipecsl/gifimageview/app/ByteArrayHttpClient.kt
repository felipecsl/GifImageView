package com.felipecsl.gifimageview.app

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.IOUtils
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder

object ByteArrayHttpClient {
    private const val TAG = "ByteArrayHttpClient"
    private val CLIENT = OkHttpClient()
    operator fun get(urlString: String?): ByteArray? {
        var inpustStream: InputStream? = null
        try {
            val decodedUrl = URLDecoder.decode(urlString, "UTF-8")
            val url = URL(decodedUrl)
            val request = Request.Builder().url(url).build()
            val response = CLIENT.newCall(request).execute()
            inpustStream = response.body()?.byteStream()
            return IOUtils.toByteArray(inpustStream)
        } catch (e: MalformedURLException) {
            Log.d(TAG, "Malformed URL", e)
        } catch (e: OutOfMemoryError) {
            Log.d(TAG, "Out of memory", e)
        } catch (e: UnsupportedEncodingException) {
            Log.d(TAG, "Unsupported encoding", e)
        } catch (e: IOException) {
            Log.d(TAG, "IO exception", e)
        } finally {
            if (inpustStream != null) {
                try {
                    inpustStream.close()
                } catch (ignored: IOException) {
                }
            }
        }
        return null
    }
}