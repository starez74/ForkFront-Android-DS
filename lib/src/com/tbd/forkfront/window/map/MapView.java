package com.tbd.forkfront.window.map;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.R;
import com.tbd.forkfront.ui.Util;

class MapView extends TextureView implements TextureView.SurfaceTextureListener {
  private final NHW_Map mMap;
  final MapRenderer mRenderer;
  final MapGestureController mGestures;

  // Rendering thread fields
  private Thread mRenderingThread;
  private volatile boolean mIsRendering;
  private volatile boolean mNeedsRedraw;
  private final Object mRenderLock = new Object();

  public MapView(NHW_Map map) {
    super(map.mContext);
    mMap = map;
    mRenderer = new MapRenderer(map);
    mGestures = new MapGestureController(map, this, mRenderer);
    setFocusable(false);
    setFocusableInTouchMode(false);

    // Set up TextureView
    setSurfaceTextureListener(this);
    setOpaque(false);

    ViewGroup mapFrame = (ViewGroup) mMap.mContext.findViewById(R.id.map_frame);
    mapFrame.addView(
        this,
        0,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    mIsRendering = false;
    mNeedsRedraw = true;

    // Set up WindowInsets listener for edge-to-edge support
    ViewCompat.setOnApplyWindowInsetsListener(
        this,
        (v, insets) -> {
          Insets systemBars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

          // Store insets for use in rendering
          mMap.mSystemInsetsTop = systemBars.top;
          mMap.mSystemInsetsBottom = systemBars.bottom;
          mMap.mSystemInsetsLeft = systemBars.left;
          mMap.mSystemInsetsRight = systemBars.right;

          // Update view bounds to account for insets
          mMap.updateViewBounds();

          // Request a redraw with new bounds
          mNeedsRedraw = true;

          return insets;
        });
  }

  // TextureView.SurfaceTextureListener implementation

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    mIsRendering = true;
    mNeedsRedraw = true;

    // Register preference change listener
    if (mRenderer.mPrefListener != null) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
      prefs.registerOnSharedPreferenceChangeListener(mRenderer.mPrefListener);
    }

    // Re-apply tile filtering in case preference changed while surface was destroyed
    mRenderer.updateTileFiltering();
    mRenderer.updateGameBackgroundColor();

    mRenderingThread = new Thread(new RenderLoop(), "NHW_Map-Render");
    mRenderingThread.start();
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    // Trigger a redraw when surface dimensions change
    mNeedsRedraw = true;
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    mIsRendering = false;
    synchronized (mRenderLock) {
      mRenderLock.notify();
    }
    if (mRenderingThread != null) {
      try {
        mRenderingThread.join(1000); // Wait up to 1 second for thread to finish
      } catch (InterruptedException e) {
        // Thread was interrupted, continue cleanup
      }
      mRenderingThread = null;
    }

    // Unregister preference listener
    if (mRenderer.mPrefListener != null) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
      prefs.unregisterOnSharedPreferenceChangeListener(mRenderer.mPrefListener);
    }

    return true;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

  // Render Loop

  private class RenderLoop implements Runnable {
    @Override
    public void run() {
      while (mIsRendering) {
        // Only render if we need to redraw
        if (mNeedsRedraw) {
          Canvas canvas = null;
          try {
            canvas = lockCanvas();
            if (canvas != null) {
              // Clear the canvas before drawing
              canvas.drawColor(mMap.mGameBackgroundColor);

              mRenderer.draw(canvas, mMap.isTTY());
              mNeedsRedraw = false;
            }
          } finally {
            if (canvas != null) {
              unlockCanvasAndPost(canvas);
            }
          }
        } else {
          // Wait until notified instead of busy-waiting
          synchronized (mRenderLock) {
            while (!mNeedsRedraw && mIsRendering) {
              try {
                mRenderLock.wait();
              } catch (InterruptedException e) {
                break;
              }
            }
          }
        }
      }
    }
  }

  public void requestRedraw() {
    mNeedsRedraw = true;
    synchronized (mRenderLock) {
      mRenderLock.notify();
    }
  }

  public void showInternal() {
    setVisibility(View.VISIBLE);
    mNeedsRedraw = true;
  }

  public void hideInternal() {
    setVisibility(View.INVISIBLE);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int w = Math.max(getViewWidth(), getSuggestedMinimumWidth());
    int h = Math.max(getViewHeight(), getSuggestedMinimumHeight());

    int wMode = MeasureSpec.getMode(widthMeasureSpec);
    int hMode = MeasureSpec.getMode(heightMeasureSpec);

    int wConstraint = MeasureSpec.getSize(widthMeasureSpec);
    int hConstraint = MeasureSpec.getSize(heightMeasureSpec);

    if (wMode == MeasureSpec.AT_MOST && w > wConstraint || wMode == MeasureSpec.EXACTLY)
      w = wConstraint;
    if (hMode == MeasureSpec.AT_MOST && h > hConstraint || hMode == MeasureSpec.EXACTLY)
      h = hConstraint;

    setMeasuredDimension(w, h);

    post(
        new Runnable() {
          @Override
          public void run() {
            mMap.centerView(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
          }
        });
  }

  @Override
  public boolean onTrackballEvent(MotionEvent event) {
    return mGestures.onTrackball(event);
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (mGestures.onTouch(event)) return true;
    return super.onTouchEvent(event);
  }

  public void setBlockingInternal(boolean bBlocking) {
    if (bBlocking) {
      String blockMsg =
          Util.hasPhysicalKeyboard(mMap.mContext) ? "Press any key to continue" : "Tap to continue";
      TextView tv = (TextView) mMap.mContext.findViewById(R.id.nh_blockmsg);
      tv.setText(blockMsg);
      tv.setVisibility(View.VISIBLE);
    } else {
      mMap.mContext.findViewById(R.id.nh_blockmsg).setVisibility(View.GONE);
      mGestures.resetState();
    }
  }

  public boolean handleKeyDown(int nhKey, int keyCode) {
    return mGestures.handleKeyDown(nhKey, keyCode);
  }

  public boolean handleKeyUp(int keyCode) {
    return mGestures.handleKeyUp(keyCode);
  }

  int getViewWidth() {
    return (int) (NHW_Map.TileCols * mRenderer.getScaledTileWidth() + 0.5f);
  }

  int getViewHeight() {
    return (int) (NHW_Map.TileRows * mRenderer.getScaledTileHeight() + 0.5f);
  }
}
