package com.felipecsl.gifimageview.app

import android.os.Bundle
import android.widget.GridView
import androidx.appcompat.app.AppCompatActivity
import java.util.ArrayList

class GridViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grid)
        val imageUrls: MutableList<String> = ArrayList(NUMBER_CELLS)
        for (i in 0 until NUMBER_CELLS) {
            imageUrls.add("https://cloud.githubusercontent.com/assets/4410820/11539468/c4d62a9c-9959-11e5-908e-cf50a21ac0e9.gif")
        }
        val adapter = GifGridAdapter(this, imageUrls)
        val gridView = findViewById<GridView>(R.id.gridView)
        gridView.adapter = adapter
    }

    companion object {
        private const val NUMBER_CELLS = 50
    }
}