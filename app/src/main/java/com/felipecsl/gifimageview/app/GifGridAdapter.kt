package com.felipecsl.gifimageview.app

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import com.felipecsl.gifimageview.app.GifDataDownloader.GifDataDownloaderCallback
import com.felipecsl.gifimageview.app.GifDataDownloader.downloadGifData
import com.felipecsl.gifimageview.library.GifImageView

class GifGridAdapter(private val context: Context, private val imageUrls: List<String>) :
    BaseAdapter() {
    override fun getCount(): Int {
        return imageUrls.size
    }

    override fun getItem(position: Int): Any {
        return imageUrls[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        val imageView: GifImageView
        if (convertView == null) {
            imageView = GifImageView(context)
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setPadding(10, 10, 10, 10)
            val size = AbsListView.LayoutParams.WRAP_CONTENT
            val layoutParams = AbsListView.LayoutParams(size, size)
            imageView.layoutParams = layoutParams
        } else {
            imageView = convertView as GifImageView
            imageView.clear()
        }
        downloadGifData(imageUrls[position], object : GifDataDownloaderCallback {
            override fun onGifDownloaded(bytes: ByteArray?) {
                // Do something with the downloaded GIF data
                imageView.setBytes(bytes)
                imageView.startAnimation()
            }
        })
        return imageView
    }
}