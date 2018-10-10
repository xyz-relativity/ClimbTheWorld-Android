package com.climbtheworld.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;

import java.util.HashMap;
import java.util.Map;

public class MappingUtils {
    private static Map<String, Bitmap> iconCache = new HashMap<>();

    public static Bitmap getPoiIcon(Context parent, GeoNode poi) {
        return getPoiIcon(parent, poi, 1);
    }

    public static Bitmap getPoiIcon(Context parent, GeoNode poi, double sizeFactor) {
        String gradeValue = GradeConverter.getConverter().
                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId());
        String mapKey = gradeValue + "|" + sizeFactor;

        if (!iconCache.containsKey(mapKey)) {
            int originalH = 193;
            int originalW = 122;
            int heightC = Math.round(Globals.sizeToDPI(parent, originalH));
            int widthC = Math.round(Globals.sizeToDPI(parent, originalW));

            LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View newViewElement = inflater.inflate(R.layout.icon_topo_display, null);
            ((TextView) newViewElement.findViewById(R.id.textPinGrade)).setText(gradeValue);

            ((ImageView) newViewElement.findViewById(R.id.imagePinGrade)).setImageTintList(Globals.gradeToColorState(poi.getLevelId()));

            final int height = View.MeasureSpec.makeMeasureSpec(heightC, View.MeasureSpec.EXACTLY);
            final int width = View.MeasureSpec.makeMeasureSpec(widthC, View.MeasureSpec.EXACTLY);
            newViewElement.measure(width, height);
            newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

            newViewElement.setDrawingCacheEnabled(true);
            newViewElement.buildDrawingCache(true);
            iconCache.put(mapKey,
                    Bitmap.createScaledBitmap(newViewElement.getDrawingCache(),
                            (int)Math.round(originalW * sizeFactor),
                            (int)Math.round(originalH * sizeFactor), true));
        }

        return iconCache.get(mapKey);
    }
}
