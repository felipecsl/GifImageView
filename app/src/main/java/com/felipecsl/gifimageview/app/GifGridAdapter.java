package com.felipecsl.gifimageview.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.felipecsl.gifimageview.library.GifImageView;

import java.util.List;

public class GifGridAdapter extends BaseAdapter {
  private final Context context;
  private final List<String> imageUrls;

  public GifGridAdapter(Context context, List<String> imageUrls) {
    this.context = context;
    this.imageUrls = imageUrls;
  }

  @Override
  public int getCount() {
    return imageUrls.size();
  }

  @Override
  public Object getItem(int position) {
    return imageUrls.get(position);
  }

  @Override
  public long getItemId(int position) {
    return 0;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    final GifImageView imageView;
    if (convertView == null) {
      imageView = new GifImageView(context);
      imageView.setScaleType(ImageView.ScaleType.CENTER);
      imageView.setPadding(10, 10, 10, 10);
      int size = AbsListView.LayoutParams.WRAP_CONTENT;
      AbsListView.LayoutParams layoutParams = new GridView.LayoutParams(size, size);
      imageView.setLayoutParams(layoutParams);
    } else {
      imageView = (GifImageView) convertView;
      imageView.clear();
    }
    new GifDataDownloader() {
      @Override
      protected void onPostExecute(final byte[] bytes) {
        imageView.setBytes(bytes);
        imageView.startAnimation();
      }
    }.execute(imageUrls.get(position));
    return imageView;
  }
}
