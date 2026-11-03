package com.tbd.forkfront.window.map;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.preference.PreferenceManager;
import com.tbd.forkfront.ui.Util;

class MapViewport {
  private static final double ZOOM_BASE = 1.005;

  enum Travel {
    Never,
    AfterPan,
    Always
  }

  final NHW_Map mMap;
  final MapView mView;
  final MapRenderer mRenderer;

  float mScale;
  float mScaleCount;
  float mMinScaleCount;
  float mMaxScaleCount;
  float mZoomStep;
  int mStickyZoom;
  boolean mIsStickyZoom;
  PointF mViewOffset;
  RectF mCanvasRect;
  float mLockTopMargin;
  float mMinTileH;
  float mMaxTileH;

  float getMinTileSizeDp() {
    // For xxxhdpi (4K phones), allow smaller tiles
    if (mMap.mDisplayDensity >= 4.0f) return 8.f;
    if (mMap.mDisplayDensity >= 3.0f) return 6.f;
    return 5.f;
  }

  float getMaxTileSizeDp() {
    // For tablets and foldables, allow larger tiles
    if (mMap.mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_XLARGE) return 150.f;
    if (mMap.mScreenSizeClass == Configuration.SCREENLAYOUT_SIZE_LARGE) return 120.f;
    return 100.f;
  }

  MapViewport(NHW_Map map, MapView view, MapRenderer renderer) {
    mMap = map;
    mView = view;
    mRenderer = renderer;

    mScale = 1.f;
    mScaleCount = 0.f;
    mMinScaleCount = -200.f;
    mMaxScaleCount = 200.f;
    mZoomStep = 20.f;
    mStickyZoom = 0;
    mIsStickyZoom = true;
    mViewOffset = new PointF(0, 0);
    mCanvasRect = new RectF();

    mRenderer.setViewport(this);
    mView.mGestures.setViewport(this);

    loadZoomLevel();
  }

  void cliparound(final int tileX, final int tileY, final int playerX, final int playerY) {
    mMap.mPlayerPos.x = playerX;
    mMap.mPlayerPos.y = playerY;

    centerView(playerX, playerY);
  }

  void centerView(final int tileX, final int tileY) {
    float tileW = mRenderer.getScaledTileWidth();
    float tileH = mRenderer.getScaledTileHeight();

    float ofsX, ofsY;
    if (shouldLockView(tileW, tileH)) {
      ofsX = mCanvasRect.left + (mCanvasRect.width() - tileW * NHW_Map.TileCols) * .5f;

      float hDiff = mCanvasRect.height() - tileH * NHW_Map.TileRows;
      float margin = Math.min(mLockTopMargin, hDiff);
      ofsY = mCanvasRect.top + (hDiff + margin) * .5f;
    } else {
      ofsX = mCanvasRect.left + (mCanvasRect.width() - tileW) * .5f - tileW * tileX;
      ofsY = mCanvasRect.top + (mCanvasRect.height() - tileH) * .5f - tileH * tileY;
    }

    if (mViewOffset.x != ofsX || mViewOffset.y != ofsY) {
      mViewOffset.set(ofsX, ofsY);
      mView.requestRedraw();
      mMap.notifyViewportChanged();
    }
  }

  boolean shouldLockView() {
    return shouldLockView(mRenderer.getScaledTileWidth(), mRenderer.getScaledTileHeight());
  }

  boolean shouldLockView(float tileW, float tileH) {
    // In fixed tablet mode, keep the player centred even if an entire level fits
    // in the available viewport. Never lock the camera to the map's top-left.
    if (mMap.mContext instanceof com.tbd.forkfront.ForkFront
        && ((com.tbd.forkfront.ForkFront) mMap.mContext).isFixedTabletLayout()) {
      return false;
    }
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
    if (!prefs.getBoolean("lockView", true)) return false;

    return tileW * NHW_Map.TileCols <= mCanvasRect.width()
        && tileH * NHW_Map.TileRows <= mCanvasRect.height();
  }

  void zoom(float amount) {
    if (amount == 0) return;
    zoomForced(amount);
  }

  void zoomForced(float amount) {
    if (mView == null) return;

    float ofsX =
        (mViewOffset.x - mCanvasRect.left - mCanvasRect.width() * 0.5f) / mView.getViewWidth();
    float ofsY =
        (mViewOffset.y - mCanvasRect.top - mCanvasRect.height() * 0.5f) / mView.getViewHeight();

    mScaleCount = Math.min(Math.max(mScaleCount + amount, mMinScaleCount), mMaxScaleCount);

    mScale = (float) Math.pow(ZOOM_BASE, mScaleCount);

    if (canPan()) {
      ofsX = mCanvasRect.left + ofsX * mView.getViewWidth() + mCanvasRect.width() * 0.5f;
      ofsY = mCanvasRect.top + ofsY * mView.getViewHeight() + mCanvasRect.height() * 0.5f;

      mViewOffset.set(ofsX, ofsY);
    } else {
      centerView(0, 0);
    }
    mView.requestRedraw();
    mMap.notifyViewportChanged();
  }

  void resetZoom() {
    zoom(-mScaleCount);
    centerView(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
  }

  void updateZoomLimits() {
    float minScale = mMinTileH / mRenderer.getBaseTileHeight();
    float maxScale = mMaxTileH / mRenderer.getBaseTileHeight();

    float amount;
    if (mMaxScaleCount - mMinScaleCount < 1) amount = 0.5f;
    else amount = (mScaleCount - mMinScaleCount) / (mMaxScaleCount - mMinScaleCount);

    mMinScaleCount = (float) (Math.log(minScale) / Math.log(ZOOM_BASE));
    mMaxScaleCount = (float) (Math.log(maxScale) / Math.log(ZOOM_BASE));

    mZoomStep = (mMaxScaleCount - mMinScaleCount) / 20;
    mScaleCount = mMinScaleCount + amount * (mMaxScaleCount - mMinScaleCount);

    zoomForced(0);
  }

  void updateViewBounds() {
    // Adjust lock top margin to account for system insets (status bar + top notch/cutout)
    mLockTopMargin = mMap.mStatus.getHeight() + mMap.mSystemInsetsTop;

    // Recalculate zoom limits with new bounds
    updateZoomLimits();

    // Re-center view if needed
    centerView(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
  }

  void pan(float dx, float dy) {
    if (canPan()) {
      mViewOffset.offset(dx, dy);
      mView.requestRedraw();
      mMap.notifyViewportChanged();
    }
  }

  boolean canPan() {
    return travelAfterPan() || !shouldLockView();
  }

  boolean travelAfterPan() {
    return getTravelOption() == Travel.AfterPan;
  }

  Travel getTravelOption() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
    // Convert old option
    if (prefs.contains("travelAfterPan")) {
      boolean oldValue = prefs.getBoolean("travelAfterPan", true);
      SharedPreferences.Editor editor = prefs.edit();
      editor.remove("travelAfterPan");
      editor.putString("travelOnClick", oldValue ? "1" : "0");
      editor.apply();
    }
    int setting = Util.parseInt(prefs.getString("travelOnClick", "1"), 1);
    if (setting == 0) return Travel.Never;
    if (setting == 1) return Travel.AfterPan;
    return Travel.Always;
  }

  void viewAreaChanged(Rect viewRect) {
    mCanvasRect.set(viewRect);
    mMap.centerView(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
  }

  void saveZoomLevel() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
    prefs.edit().putFloat("zoomLevel", mScaleCount).apply();
  }

  void loadZoomLevel() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mMap.mContext);
    float zoomLevel = 0;
    try {
      zoomLevel = prefs.getFloat("zoomLevel", 0.f);
    } catch (Exception e) {
    }
    zoomForced(-mScaleCount);
    // The tablet workspace uses slightly smaller, legible map glyphs by default.
    if (mMap.mContext instanceof com.tbd.forkfront.ForkFront
        && ((com.tbd.forkfront.ForkFront) mMap.mContext).isFixedTabletLayout()) {
      zoomForced(-65.f);
      centerView(mMap.mPlayerPos.x, mMap.mPlayerPos.y);
    }
  }

  PointF getViewOffset() {
    return new PointF(mViewOffset.x, mViewOffset.y);
  }

  float getScale() {
    return mScale;
  }

  RectF getCanvasRect() {
    return new RectF(mCanvasRect);
  }
}
