package com.felipecsl.gifimageview.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;

public class Blur {
  private static final float BLUR_RADIUS = 25f;

  private final RenderScript rs;
  private ScriptIntrinsicBlur script;
  private Allocation input;
  private Allocation output;
  private boolean configured = false;
  private Bitmap tmp;
  private int[] pixels;

  public static Blur newInstance(Context context) {
    return new Blur(context);
  }

  private Blur(Context context) {
    rs = RenderScript.create(context);
  }

  public Bitmap blur(Bitmap image) {
    if (image == null)
      return null;

    image = RGB565toARGB888(image);
    if (!configured) {
      input = Allocation.createFromBitmap(rs, image);
      output = Allocation.createTyped(rs, input.getType());
      script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
      script.setRadius(BLUR_RADIUS);
      configured = true;
    } else
      input.copyFrom(image);

    script.setInput(input);
    script.forEach(output);
    output.copyTo(image);

    return image;
  }

  private Bitmap RGB565toARGB888(Bitmap img) {
    int numPixels = img.getWidth() * img.getHeight();

    //Create a Bitmap of the appropriate format.
    if (tmp == null) {
      tmp = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
      pixels = new int[numPixels];
    }

    //Get JPEG pixels.  Each int is the color values for one pixel.
    img.getPixels(pixels, 0, img.getWidth(), 0, 0, img.getWidth(), img.getHeight());

    //Set RGB pixels.
    tmp.setPixels(pixels, 0, tmp.getWidth(), 0, 0, tmp.getWidth(), tmp.getHeight());

    return tmp;
  }
}
