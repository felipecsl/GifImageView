package com.felipecsl.gifimageview.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class GifImageView extends ImageView implements Runnable {

    private static final String TAG = "GifDecoderView";
    private GifDecoder gifDecoder;
    private Bitmap tmpBitmap;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean animating;
    private boolean shouldClear;
    private Thread animationThread;
    private OnFrameAvailable frameCallback = null;
    private long framesDisplayDuration = -1l;

    private final Runnable updateResults = new Runnable() {
        @Override
        public void run() {
            if (tmpBitmap != null && !tmpBitmap.isRecycled())
                setImageBitmap(tmpBitmap);
        }
    };

    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            if (tmpBitmap != null && !tmpBitmap.isRecycled())
                tmpBitmap.recycle();
            tmpBitmap = null;
            gifDecoder = null;
            animationThread = null;
            shouldClear = false;
        }
    };

    public GifImageView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public GifImageView(final Context context) {
        super(context);
    }

    public void setBytes(final byte[] bytes) {
        gifDecoder = new GifDecoder();
        try {
            gifDecoder.read(bytes);
        } catch (final OutOfMemoryError e) {
            gifDecoder = null;
            Log.e(TAG, e.getMessage(), e);
            return;
        }

        if (canStart()) {
            animationThread = new Thread(this);
            animationThread.start();
        }
    }

    public long getFramesDisplayDuration() {
        return framesDisplayDuration;
    }

    /**
     * Sets custom display duration in milliseconds for the all frames. Should be called before {@link #startAnimation()}
     * @param framesDisplayDuration Duration in milliseconds. Default value = -1, this property will be ignored and default delay from gif file will be used.
     */
    public void setFramesDisplayDuration(long framesDisplayDuration) {
        this.framesDisplayDuration = framesDisplayDuration;
    }

    public void startAnimation() {
        animating = true;

        if (canStart()) {
            animationThread = new Thread(this);
            animationThread.start();
        }
    }

    public boolean isAnimating() {
        return animating;
    }

    public void stopAnimation() {
        animating = false;

        if (animationThread != null) {
            animationThread.interrupt();
            animationThread = null;
        }
    }

    public void clear() {
        animating = false;
        shouldClear = true;
        stopAnimation();
    }

    private boolean canStart() {
        return animating && gifDecoder != null && animationThread == null;
    }

    public int getGifWidth() {
        return gifDecoder.getWidth();
    }

    public int getGifHeight() {
        return gifDecoder.getHeight();
    }

    @Override
    public void run() {
        if (shouldClear) {
            handler.post(cleanupRunnable);
            return;
        }

        final int n = gifDecoder.getFrameCount();
        do {
            for (int i = 0; i < n; i++) {
                if (!animating)
                    break;
                try {
                    tmpBitmap = gifDecoder.getNextFrame();
                    if (frameCallback != null)
                        tmpBitmap = frameCallback.onFrameAvailable(tmpBitmap);

                    if (!animating)
                        break;
                    handler.post(updateResults);
                } catch (final ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                    Log.w(TAG, e);
                }
                if (!animating)
                    break;
                gifDecoder.advance();
                try {
                    Thread.sleep(framesDisplayDuration > 0 ? framesDisplayDuration : gifDecoder.getNextDelay());
                } catch (final Exception e) {
                    // suppress any exception
                    // it can be InterruptedException or IllegalArgumentException
                }
            }
        } while (animating);
    }

    public OnFrameAvailable getOnFrameAvailable() {
        return frameCallback;
    }

    public void setOnFrameAvailable(OnFrameAvailable frameProcessor) {
        this.frameCallback = frameProcessor;
    }

    public interface OnFrameAvailable {
        public Bitmap onFrameAvailable(Bitmap bitmap);
    }
}
