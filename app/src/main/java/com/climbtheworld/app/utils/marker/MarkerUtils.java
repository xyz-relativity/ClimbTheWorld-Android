package com.climbtheworld.app.utils.marker;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class MarkerUtils {
    private final static float scale = Resources.getSystem().getDisplayMetrics().density;
    private static final String UNKNOWN_TYPE = "-?-";

    public enum IconType {
        poiIcon(200, 270, (int) Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE * 0.74), DisplayableGeoNode.POI_ICON_DP_SIZE),
        poiCLuster(48, 48, DisplayableGeoNode.POI_ICON_DP_SIZE, DisplayableGeoNode.POI_ICON_DP_SIZE);

        private final int measuredHeight;
        private final int measuredWidth;
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

            measuredHeight = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.sizeToDPI(originalH)), View.MeasureSpec.AT_MOST);
            measuredWidth = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.sizeToDPI(originalW)), View.MeasureSpec.AT_MOST);
        }
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        return getPoiIcon(parent, poi, DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, int alpha) {
        Bitmap bitmap;
        switch (poi.getNodeType()) {
            case crag:
                bitmap = createBitmapFromLayout(parent, IconType.poiIcon, View.inflate(parent, R.layout.icon_node_crag_display, null), poi);
                break;

            case artificial:
                bitmap = createBitmapFromLayout(parent, IconType.poiIcon, View.inflate(parent, R.layout.icon_node_gym_display, null), poi);
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
        Bitmap bitmap = createBitmapFromLayout(parent, IconType.poiIcon, View.inflate(parent, layoutID, null), null);
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

        View newViewElement = View.inflate(parent, R.layout.icon_node_topo_display, null);

        ((TextView) newViewElement.findViewById(R.id.textPinGrade)).setText(gradeValue);
        ((TextView) newViewElement.findViewById(R.id.textRouteTitle)).setText(poi.getName());

        ((ImageView) newViewElement.findViewById(R.id.imagePin)).setImageTintList(color);
        ((ImageView) newViewElement.findViewById(R.id.imagePinInfo)).setImageTintList(color);

        Set<GeoNode.ClimbingStyle> styles = poi.getClimbingStyles();
        List<Drawable> stylesDrawables = new ArrayList<>();
        if (styles.isEmpty()) {
            ((ImageView) newViewElement.findViewById(R.id.imagePinType)).setVisibility(View.INVISIBLE);
        } else {
            for (GeoNode.ClimbingStyle style : poi.getClimbingStyles()) {
                stylesDrawables.add(ResourcesCompat.getDrawable(parent.getResources(), style.getIconResource(), null));
            }
            LayerDrawable finalDrawable = new LayerDrawable(stylesDrawables.toArray(new Drawable[0]));
            ((ImageView) newViewElement.findViewById(R.id.imagePinType)).setImageDrawable(finalDrawable);
        }

        newViewElement.measure(iconType.measuredWidth, iconType.measuredHeight);
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

        if (poi != null) {
            ((TextView) newViewElement.findViewById(R.id.textRouteTitle)).setText(poi.getName());
        }

        newViewElement.measure(iconType.measuredWidth, iconType.measuredHeight);
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
