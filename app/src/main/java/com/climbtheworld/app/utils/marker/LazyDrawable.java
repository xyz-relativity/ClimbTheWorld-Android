package com.climbtheworld.app.utils.marker;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.views.MapView;

import java.util.concurrent.Semaphore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class LazyDrawable extends Drawable {
    private final MapView mapView;
    Drawable cachedDrawable = null;
    final AppCompatActivity parent;
    final GeoNode poi;
    ColorFilter colorFilter = null;
    int alpha = 255;
    private boolean isDirty = true;
    private final float anchorU;
    private final float anchorV;
    private static final Semaphore refreshLock = new Semaphore(1);

    public LazyDrawable(AppCompatActivity parent, MapView mapView, GeoNode poi, float anchorU, float anchorV) {
        super();
        this.parent = parent;
        this.poi = poi;
        this.mapView = mapView;
        this.anchorU = anchorU;
        this.anchorV = anchorV;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (cachedDrawable != null) {
            System.out.println("img: ======================================================");
            if (colorFilter != null) {
                cachedDrawable.setColorFilter(colorFilter);
            }
            Point mPositionPixels = new Point();
            mapView.getProjection().toPixels(Globals.poiToGeoPoint(poi), mPositionPixels);
            final int offsetX = mPositionPixels.x - Math.round(cachedDrawable.getIntrinsicWidth() * anchorU);
            final int offsetY = mPositionPixels.y - Math.round(cachedDrawable.getIntrinsicHeight() * anchorV);
            canvas.drawBitmap(((BitmapDrawable) cachedDrawable).getBitmap(), offsetX, offsetY, null);
        }
        if ((cachedDrawable == null || isDirty) && refreshLock.tryAcquire()) {
            System.out.println("img: +++++++++++++++++++++++++++++++++++++++++++++++++++++" + " " + isDirty + " " + alpha);
            isDirty = false;
            Constants.ASYNC_TASK_EXECUTOR.execute(new Runnable() {
                public void run() {
                    cachedDrawable = MarkerUtils.getPoiIcon(parent, poi, alpha);
                    refreshLock.release();
                }
            });
        }
    }

    @Override
    public int getIntrinsicWidth() {
        if (cachedDrawable != null) {
            return cachedDrawable.getIntrinsicWidth();
        } else {
            return 0;
        }
    }

    @Override
    public int getIntrinsicHeight() {
        if (cachedDrawable != null) {
            return cachedDrawable.getIntrinsicHeight();
        } else {
            return 0;
        }
    }

    @Override
    public void setAlpha(int i) {
        this.alpha = i;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setDirty() {
        isDirty = true;
    }

    public Drawable getDrawable() {
        return MarkerUtils.getPoiIcon(parent, poi, alpha);
    }
}
