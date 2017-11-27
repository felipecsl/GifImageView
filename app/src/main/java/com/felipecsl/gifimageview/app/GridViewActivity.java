package com.felipecsl.gifimageview.app;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.GridView;

import java.util.ArrayList;
import java.util.List;

public class GridViewActivity extends AppCompatActivity {
  private static final int NUMBER_CELLS = 50;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_grid);

    List<String> imageUrls = new ArrayList<>(NUMBER_CELLS);
    for (int i = 0; i < NUMBER_CELLS; i++) {
      imageUrls.add("https://cloud.githubusercontent.com/assets/4410820/11539468/c4d62a9c-9959-11e5-908e-cf50a21ac0e9.gif");
    }

    GifGridAdapter adapter = new GifGridAdapter(this, imageUrls);
    GridView gridView = findViewById(R.id.gridView);
    gridView.setAdapter(adapter);
  }
}
