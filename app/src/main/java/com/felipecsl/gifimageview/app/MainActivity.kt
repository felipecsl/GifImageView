package com.felipecsl.gifimageview.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.felipecsl.gifimageview.app.Blur.Companion.newInstance
import com.felipecsl.gifimageview.app.GifDataDownloader.GifDataDownloaderCallback
import com.felipecsl.gifimageview.app.GifDataDownloader.downloadGifData
import com.felipecsl.gifimageview.library.GifImageView
import com.felipecsl.gifimageview.library.GifImageView.OnAnimationStop
import com.felipecsl.gifimageview.library.GifImageView.OnFrameAvailable

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private var gifImageView: GifImageView? = null
    private var btnToggle: Button? = null
    private var btnBlur: Button? = null
    private var shouldBlur = false
    private var blur: Blur? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        gifImageView = findViewById(R.id.gifImageView)
        btnToggle = findViewById(R.id.btnToggle)
        btnBlur = findViewById(R.id.btnBlur)
        val btnClear = findViewById<Button>(R.id.btnClear)
        blur = newInstance(this)
        gifImageView?.onFrameAvailable = object : OnFrameAvailable {
            override fun onFrameAvailable(bitmap: Bitmap?): Bitmap? {
                return if (shouldBlur) {
                    blur!!.blur(bitmap)
                } else bitmap
            }
        }
        gifImageView?.onAnimationStop = object : OnAnimationStop {
            override fun onAnimationStop() {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Animation stopped",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        btnToggle?.setOnClickListener(this)
        btnClear.setOnClickListener(this)
        btnBlur?.setOnClickListener(this)
        downloadGifData(
            "https://i0.wp.com/www.printmag.com/wp-content/uploads/2021/02/4cbe8d_f1ed2800a49649848102c68fc5a66e53mv2.gif?fit=476%2C280&ssl=1",
            object : GifDataDownloaderCallback {
                override fun onGifDownloaded(bytes: ByteArray?) {
                    // Do something with the downloaded GIF data
                    gifImageView?.setBytes(bytes)
                    gifImageView?.startAnimation()
                    Log.d(TAG, "GIF width is " + gifImageView?.getGifWidth())
                    Log.d(TAG, "GIF height is " + gifImageView?.getGifHeight())
                }
            })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.show_grid) {
            startActivity(Intent(this, GridViewActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onClick(v: View) {
        if (v == btnToggle) {
            if (gifImageView!!.isAnimating) gifImageView!!.stopAnimation() else gifImageView!!.startAnimation()
        } else if (v == btnBlur) {
            shouldBlur = !shouldBlur
        } else {
            gifImageView!!.clear()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}