package com.climbtheworld.app.utils.marker;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class MarkerUtils {
    private final static float scale = Resources.getSystem().getDisplayMetrics().density;
    private static final String UNKNOWN_TYPE = "-?-";

    public enum IconType {
        poiIcon(200, 270, (int) Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE * 0.74), DisplayableGeoNode.POI_ICON_DP_SIZE),
        poiCLuster(48, 48, DisplayableGeoNode.POI_ICON_DP_SIZE, DisplayableGeoNode.POI_ICON_DP_SIZE);

        public int originalW;
        public int originalH;
        public int dpW;
        public int dpH;
        public int pixelW;
        public int pixelH;

        IconType(int originW, int originH, int dpW, int dpH) {
            this.originalW = originW;
            this.originalH = originH;
            this.dpW = dpW;
            this.dpH = dpH;
            pixelW = Math.round(dpW * scale + 0.5f);
            pixelH = Math.round(dpH * scale + 0.5f);
        }
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        return getPoiIcon(parent, poi, DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, int alpha) {
        Bitmap bitmap;
        LayoutInflater inflater;
        switch (poi.getNodeType()) {
            case crag:
                inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                bitmap = createBitmapFromLayout(parent, IconType.poiIcon, inflater.inflate(R.layout.icon_node_crag_display, null), poi);
                break;

            case artificial:
                inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                bitmap = createBitmapFromLayout(parent, IconType.poiIcon, inflater.inflate(R.layout.icon_node_gym_display, null), poi);
                break;

            case route:
                bitmap = createRouteBitmapFromLayout(parent, poi, IconType.poiIcon);
                break;

            case unknown:
            default:
                bitmap = createRouteBitmapFromLayout(parent, poi, IconType.poiIcon);
                break;
        }
        return toBitmapDrawableAlpha(parent, bitmap, alpha);
    }

    public static Drawable getLayoutIcon(AppCompatActivity parent, int layoutID, int alpha) {
        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Bitmap bitmap = createBitmapFromLayout(parent, IconType.poiIcon, inflater.inflate(layoutID, null), null);
        return toBitmapDrawableAlpha(parent, bitmap, alpha);
    }

    public static Drawable getClusterIcon(AppCompatActivity parent, int color, int alpha) {
        Drawable nodeIcon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_clusters, null);
        nodeIcon.setTintList(ColorStateList.valueOf(color));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);
        BitmapDrawable icon = new BitmapDrawable(parent.getResources(), MarkerUtils.getBitmap(parent, nodeIcon, MarkerUtils.IconType.poiCLuster));
        return toBitmapDrawableAlpha(parent, icon.getBitmap(), alpha);
    }

    private static Bitmap createRouteBitmapFromLayout(AppCompatActivity parent, GeoNode poi, IconType iconType) {
        String gradeValue;
        ColorStateList color;
        if (poi.getNodeType() == GeoNode.NodeTypes.unknown) {
            gradeValue = UNKNOWN_TYPE;
            color = ColorStateList.valueOf(DisplayableGeoNode.POI_DEFAULT_COLOR).withAlpha(255);
        } else {
            gradeValue = GradeSystem.fromString(Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG));
            color = Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG));
        }

        int heightC = Math.round(Globals.sizeToDPI(parent, iconType.originalH));
        int widthC = Math.round(Globals.sizeToDPI(parent, iconType.originalW));

        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.icon_node_topo_display, null);

        ((TextView) newViewElement.findViewById(R.id.textPinGrade)).setText(gradeValue);
        ((TextView) newViewElement.findViewById(R.id.textRouteTitle)).setText(poi.getName());

        ((ImageView) newViewElement.findViewById(R.id.imagePin)).setImageTintList(color);
        ((ImageView) newViewElement.findViewById(R.id.imagePinType)).setImageTintList(color);

        final int height = View.MeasureSpec.makeMeasureSpec(heightC, View.MeasureSpec.EXACTLY);
        final int width = View.MeasureSpec.makeMeasureSpec(widthC, View.MeasureSpec.EXACTLY);
        newViewElement.measure(width, height);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        newViewElement.setDrawingCacheEnabled(true);
        newViewElement.buildDrawingCache(true);
        Bitmap result = Bitmap.createScaledBitmap(newViewElement.getDrawingCache(), iconType.pixelW, iconType.pixelH, true);

        newViewElement.setDrawingCacheEnabled(false);

        return result;
    }

    private static BitmapDrawable toBitmapDrawableAlpha(AppCompatActivity parent, Bitmap originalBitmap, int alpha) {
        Bitmap newBitmap = Bitmap.createBitmap(originalBitmap.getWidth(), originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint alphaPaint = new Paint();
        alphaPaint.setAntiAlias(true);
        alphaPaint.setFilterBitmap(true);
        alphaPaint.setDither(true);
        alphaPaint.setAlpha(alpha);
        canvas.drawBitmap(originalBitmap, 0, 0, alphaPaint);
        return new BitmapDrawable(parent.getResources(), newBitmap);
    }

    private static Bitmap createBitmapFromLayout(AppCompatActivity parent, IconType iconType, View newViewElement, GeoNode poi) {
        int heightC = Math.round(Globals.sizeToDPI(parent, iconType.originalH));
        int widthC = Math.round(Globals.sizeToDPI(parent, iconType.originalW));

        final int height = View.MeasureSpec.makeMeasureSpec(heightC, View.MeasureSpec.EXACTLY);
        final int width = View.MeasureSpec.makeMeasureSpec(widthC, View.MeasureSpec.EXACTLY);

        if (poi != null) {
            ((TextView) newViewElement.findViewById(R.id.textRouteTitle)).setText(poi.getName());
        }

        newViewElement.measure(width, height);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        newViewElement.setDrawingCacheEnabled(true);
        newViewElement.buildDrawingCache(true);
        Bitmap result = Bitmap.createScaledBitmap(newViewElement.getDrawingCache(), iconType.pixelW, iconType.pixelH, true);

        newViewElement.setDrawingCacheEnabled(false);

        return result;
    }

    public static Bitmap getBitmap(AppCompatActivity parent, Drawable vectorDrawable, IconType iconType) {
        Bitmap bitmap = Bitmap.createBitmap(iconType.originalW, iconType.originalH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0,
                canvas.getWidth(),
                canvas.getHeight());
        vectorDrawable.draw(canvas);
        return Bitmap.createScaledBitmap(bitmap, iconType.pixelW, iconType.pixelH, true);
    }

    private static String getNodeStyleString(AppCompatActivity parent, GeoNode node) {
        final String separator = " ";
        StringBuilder result = new StringBuilder();
        for (GeoNode.ClimbingStyle type : node.getClimbingStyles()) {
            result.append(parent.getString(type.getShortNameId())).append(separator);
        }

        if (result.length() > 0) {
            result.delete(result.lastIndexOf(separator), result.length());
        }

        return result.toString();
    }

}
