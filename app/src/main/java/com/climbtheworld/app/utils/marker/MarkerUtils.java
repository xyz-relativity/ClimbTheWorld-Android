package com.climbtheworld.app.utils.marker;

import android.content.res.ColorStateList;
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
    private static final String UNKNOWN_TYPE = "-?-";

    public enum IconType {
        poiIcon(200, 270, (int) Math.round(DisplayableGeoNode.POI_ICON_DP_SIZE * 0.74), DisplayableGeoNode.POI_ICON_DP_SIZE),
        poiCLuster(48, 48, DisplayableGeoNode.CLUSTER_ICON_DP_SIZE, DisplayableGeoNode.CLUSTER_ICON_DP_SIZE);

        private final int measuredHeight;
        private final int measuredWidth;
        private final int iconPxWith;
        private final int iconPxHeight;

        IconType(int originWith, int originHeight, int iconDPWith, int iconDPHeight) {
            this.iconPxWith = Math.round(Globals.convertDpToPixel(iconDPWith));
            this.iconPxHeight = Math.round(Globals.convertDpToPixel(iconDPHeight));

            this.measuredWidth = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.convertDpToPixel(originWith)), View.MeasureSpec.EXACTLY);
            this.measuredHeight = View.MeasureSpec.makeMeasureSpec(Math.round(Globals.convertDpToPixel(originHeight)), View.MeasureSpec.EXACTLY);
        }
    }

    public static Drawable getClusterIcon(AppCompatActivity parent, int color, int alpha) {
        Drawable nodeIcon = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_clusters, null);
        nodeIcon.setTintList(ColorStateList.valueOf(color));
        nodeIcon.setTintMode(PorterDuff.Mode.MULTIPLY);

        Bitmap bitmap = Bitmap.createBitmap(MarkerUtils.IconType.poiCLuster.iconPxWith, MarkerUtils.IconType.poiCLuster.iconPxHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        nodeIcon.setBounds(0, 0,
                canvas.getWidth(),
                canvas.getHeight());
        nodeIcon.draw(canvas);

        BitmapDrawable icon = new BitmapDrawable(parent.getResources(), bitmap);
        return toBitmapDrawableWithAlpha(parent, icon.getBitmap(), alpha);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        return getPoiIcon(parent, poi, DisplayableGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, int alpha) {
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
                bitmap = createRouteBitmapFromLayout(parent, poi);
                break;
        }
        return toBitmapDrawableWithAlpha(parent, bitmap, alpha);
    }

    public static Drawable getLayoutIcon(AppCompatActivity parent, int layoutID, int alpha) {
        Bitmap bitmap = createPointBitmapFromLayout(View.inflate(parent, layoutID, null), null);
        return toBitmapDrawableWithAlpha(parent, bitmap, alpha);
    }

    private static Bitmap createRouteBitmapFromLayout(AppCompatActivity parent, GeoNode poi) {
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
            (newViewElement.findViewById(R.id.imagePinType)).setVisibility(View.INVISIBLE);
            (newViewElement.findViewById(R.id.imagePinTypeOutline)).setVisibility(View.INVISIBLE);
        } else {
            for (GeoNode.ClimbingStyle style : poi.getClimbingStyles()) {
                stylesDrawables.add(ResourcesCompat.getDrawable(parent.getResources(), style.getIconResource(), null));
            }
            LayerDrawable finalDrawable = new LayerDrawable(stylesDrawables.toArray(new Drawable[0]));
            ((ImageView) newViewElement.findViewById(R.id.imagePinType)).setImageDrawable(finalDrawable);
        }

        newViewElement.measure(IconType.poiIcon.measuredWidth, IconType.poiIcon.measuredHeight);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(newViewElement.getMeasuredWidth(),
                newViewElement.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable background = newViewElement.getBackground();

        if (background != null) {
            background.draw(canvas);
        }
        newViewElement.draw(canvas);

        return Bitmap.createScaledBitmap(bitmap, IconType.poiIcon.iconPxWith, IconType.poiIcon.iconPxHeight, true);
    }

    private static BitmapDrawable toBitmapDrawableWithAlpha(AppCompatActivity parent, Bitmap originalBitmap, int alpha) {
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

    private static Bitmap createPointBitmapFromLayout(View newViewElement, GeoNode poi) {

        if (poi != null) {
            ((TextView) newViewElement.findViewById(R.id.textRouteTitle)).setText(poi.getName());
        }

        newViewElement.measure(IconType.poiIcon.measuredWidth, IconType.poiIcon.measuredHeight);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        Bitmap bitmap = Bitmap.createBitmap(newViewElement.getMeasuredWidth(),
                newViewElement.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Drawable background = newViewElement.getBackground();

        if (background != null) {
            background.draw(canvas);
        }
        newViewElement.draw(canvas);

        return Bitmap.createScaledBitmap(bitmap, IconType.poiIcon.iconPxWith, IconType.poiIcon.iconPxHeight, true);
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
