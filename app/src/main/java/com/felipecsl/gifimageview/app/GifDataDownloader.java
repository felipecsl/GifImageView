package com.felipecsl.gifimageview.app;

import android.util.Log;

public class GifDataDownloader {
  private static final String TAG = "GifDataDownloader";

  public interface GifDataDownloaderCallback {
    void onGifDownloaded(byte[] gifData);
  }

  public static void downloadGifData(final String gifUrl, final GifDataDownloaderCallback callback) {
    if (gifUrl == null)
      return;

    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          final byte[] gifData = ByteArrayHttpClient.get(gifUrl);
          if (callback != null) {
            callback.onGifDownloaded(gifData);
          }
        } catch (OutOfMemoryError e) {
          Log.e(TAG, "GifDecode OOM: " + gifUrl, e);
        }
      }
    }).start();
  }
}
