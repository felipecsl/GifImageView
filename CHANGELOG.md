# 2.2.0 (11/27/2017)

* New (#66): Expose `GifDecoder#getFrameCount`
* New (#65): Bump compile and target SDK versions to 27, `minSdkVersion` is now 14
* Fix (#58): Apply latest changes from Glide's `GifDecoder`, fix `DISPOSE_BACKGROUND` and `DISPOSE_PREVIOUS`
* New (#57): Added `OnAnimationStart` callback
* New (#35): Added support for GIFs with a loop count specified

# 2.1.0 (06/22/2016)

* Fix (#34): Clear animation when detached from the window
* New (#18): On stop callback for when animation completes
* Fix (#36): Division by Zero Exception
* Fix (#37): IllegalArgumentException due to overflowing buffer length
* Fix (#28): Memory leak in cleanupRunnable

# 2.0.0 (10/27/2015)

* Ported the `GifDecoder` implementation from [Glide](https://github.com/bumptech/glide) which fixes
most of the gif weirdnesses and bugs.

# 1.2.0

* Adds support for custom frame display duration via `setFramesDisplayDuration()`, thanks to @donvigo

# 1.1.0

* Adds support to ``OnFrameAvailable`` callback, thanks to @luciofm.

# 1.0.6

* Adds methods ``getGifWidth()`` and ``getGifHeight()`` to ``GifImageView`` class.

# 1.0.5

* Catch exceptions in ``GifImageView.run()``

# 1.0.4

* Safer clear() routine to avoid null pointer exceptions.

# 1.0.3

* PR #5: Fixes crashes when clearing the GIF bytes.

# 1.0.2

* PR #4: Replace false image data decoding in readContents with simple skipping.

# 1.0.1

* Adds ``isAnimating()`` that returns whether the gif animation is currently running.

# 1.0.0

Initial release