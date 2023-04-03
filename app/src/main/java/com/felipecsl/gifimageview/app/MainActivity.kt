package com.felipecsl.gifimageview.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.felipecsl.gifimageview.app.Blur.Companion.newInstance
import com.felipecsl.gifimageview.app.GifDataDownloader.GifDataDownloaderCallback
import com.felipecsl.gifimageview.app.GifDataDownloader.downloadGifData
import com.felipecsl.gifimageview.library.GifImageView
import com.felipecsl.gifimageview.library.GifImageView.OnAnimationStop
import com.felipecsl.gifimageview.library.GifImageView.OnFrameAvailable

class MainActivity : AppCompatActivity() {

    private var gifImageView: GifImageView? = null
    private var btnToggle: AppCompatButton? = null
    private var btnBlur: AppCompatButton? = null
    private var btnClear: AppCompatButton? = null
    private var shouldBlur = false
    private var blur: Blur? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gifImageView = findViewById(R.id.gifImageView)
        btnToggle = findViewById(R.id.btnToggle)
        btnBlur = findViewById(R.id.btnBlur)
        btnClear = findViewById(R.id.btnClear)
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
        initClicks()
        initViewGif()
        setStyle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.show_grid) {
            //startActivity(Intent(this, GridViewActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initViewGif() {
        downloadGifData(
            getString(R.string.gif_url),
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

    private fun initClicks() {
        btnToggle?.setOnClickListener {
            if (gifImageView?.isAnimating == true) {
                gifImageView?.stopAnimation()
                btnToggle?.text = getString(R.string.start_gif)
            } else {
                gifImageView?.startAnimation()
                btnToggle?.text = getString(R.string.stop_gif)
            }
        }

        btnClear?.setOnClickListener { gifImageView?.clear() }

        btnBlur?.setOnClickListener { shouldBlur = !shouldBlur }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private fun setStyle(){
        btnToggle?.setBackgroundColor(Color.parseColor("#ff9e22"))
        btnBlur?.setBackgroundColor(Color.parseColor("#cd00ea"))
        btnClear?.setBackgroundColor(Color.parseColor("#D50000"))


        btnToggle?.setTextColor(Color.parseColor("#F3E5F5"))
        btnBlur?.setTextColor(Color.parseColor("#F3E5F5"))
        btnClear?.setTextColor(Color.parseColor("#F3E5F5"))
    }
}