package com.felipecsl.gifimageview.app;

import android.os.AsyncTask;
import android.util.Log;

public class GifDataDownloader extends AsyncTask<String, Void, byte[]> {
  private static final String TAG = "GifDataDownloader";

  @Override protected byte[] doInBackground(final String... params) {
    final String gifUrl = params[0];

    if (gifUrl == null)
      return null;

    try {
      return ByteArrayHttpClient.get(gifUrl);
    } catch (OutOfMemoryError e) {
      Log.e(TAG, "GifDecode OOM: " + gifUrl, e);
      return null;
    }
  }
}
