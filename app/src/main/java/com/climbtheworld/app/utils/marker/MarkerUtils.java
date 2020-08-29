package com.climbtheworld.app.utils.marker;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class MarkerUtils {
    public static final String UNKNOWN_TYPE = "-?-";
    private static final HashMap<String, Drawable> iconCache = new HashMap<>();

    public enum IconType {
        poiRouteIcon(200, 300, Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE)),
        poiGymCragIcon(200, 270, Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE)),
        poiCLuster(48, 48, DisplayableGeoNode.CLUSTER_ICON_DP_SIZE);

        private final int measuredHeight;
        private final int measuredWidth;
        private final int iconPxWith;
        private final int iconPxHeight;

        IconType(int originWith, int originHeight, int iconDP) {
            float aspectRatio = (float)originWith / (float)originHeight;
            this.iconPxWith = Math.round(Globals.convertDpToPixel(iconDP * aspectRatio));
            this.iconPxHeight = Math.round(Globals.convertDpToPixel(iconDP));

            this.measuredWidth = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.convertDpToPixel(originWith)), View.MeasureSpec.EXACTLY);
            this.measuredHeight = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.convertDpToPixel(originHeight)), View.MeasureSpec.EXACTLY);
        }
    }

    public static Drawable getClusterIcon(AppCompatActivity parent, int color, int alpha) {
        final String cacheKey = "cluster" + "|" + color + "|" + alpha;

        if (!iconCache.containsKey(cacheKey)) {
            synchronized (iconCache) {
                if (!iconCache.containsKey(cacheKey)) {
                    Drawable nodeIcon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_clusters, null);
                    nodeIcon.setTintList(ColorStateList.valueOf(color));
                    nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

                    Bitmap bitmap = Bitmap.createBitmap(MarkerUtils.IconType.poiCLuster.iconPxWith, MarkerUtils.IconType.poiCLuster.iconPxHeight, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    nodeIcon.setBounds(0, 0,
                            canvas.getWidth(),
                            canvas.getHeight());
                    nodeIcon.draw(canvas);
                    iconCache.put(cacheKey, new BitmapDrawable(parent.getResources(), bitmap));
                }
            }
        }

        return iconCache.get(cacheKey);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        ColorStateList color = ColorStateList.valueOf(DisplayableGeoNode.POI_DEFAULT_COLOR).withAlpha(255);
        if (poi.getNodeType() == GeoNode.NodeTypes.route) {
            color = Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG));
        }
        return getPoiIcon(parent, poi, color);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, ColorStateList color) {
        final String cacheKey = "route" + "|" + poi.getNodeType() + "|" + color;
        if (!iconCache.containsKey(cacheKey)) {
            synchronized (iconCache) {
                if (!iconCache.containsKey(cacheKey)) {
                    Bitmap bitmap;
                    switch (poi.getNodeType()) {
                        case crag:
                            bitmap = createPointBitmapFromLayout(View.inflate(parent, R.layout.icon_node_crag_display, null), poi);
                            break;

                        case artificial:
                            bitmap = createPointBitmapFromLayout(View.inflate(parent, R.layout.icon_node_gym_display, null), poi);
                            break;

                        case route:
                        case unknown:
                        default:
                            bitmap = createRouteBitmapFromLayout(parent, poi, color);
                            break;
                    }
                    iconCache.put(cacheKey, new BitmapDrawable(parent.getResources(), bitmap));
                }
            }
        }
        return iconCache.get(cacheKey);
    }

    public static Drawable getLayoutIcon(AppCompatActivity parent, int layoutID, int alpha) {
        return new BitmapDrawable(parent.getResources(), createPointBitmapFromLayout(View.inflate(parent, layoutID, null), null));
    }

    private static Bitmap createRouteBitmapFromLayout(AppCompatActivity parent, GeoNode poi, ColorStateList color) {
        View newViewElement = View.inflate(parent, R.layout.icon_node_topo_display, null);
        ((ImageView) newViewElement.findViewById(R.id.imagePin)).setImageTintList(color);

        newViewElement.measure(IconType.poiRouteIcon.measuredWidth, IconType.poiRouteIcon.measuredHeight);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(newViewElement.getMeasuredWidth(),
                newViewElement.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable background = newViewElement.getBackground();

        if (background != null) {
            background.draw(canvas);
        }
        newViewElement.draw(canvas);
        Bitmap scaleBitmap = Bitmap.createScaledBitmap(bitmap, IconType.poiRouteIcon.iconPxWith, IconType.poiRouteIcon.iconPxHeight, true);
        bitmap.recycle();

        return scaleBitmap;
    }

    private static Bitmap createPointBitmapFromLayout(View newViewElement, GeoNode poi) {
        newViewElement.measure(IconType.poiGymCragIcon.measuredWidth, IconType.poiGymCragIcon.measuredHeight);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(newViewElement.getMeasuredWidth(),
                newViewElement.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable background = newViewElement.getBackground();

        if (background != null) {
            background.draw(canvas);
        }
        newViewElement.draw(canvas);

        Bitmap scaleBitmap = Bitmap.createScaledBitmap(bitmap, IconType.poiGymCragIcon.iconPxWith, IconType.poiGymCragIcon.iconPxHeight, true);
        bitmap.recycle();

        return scaleBitmap;
    }

}
