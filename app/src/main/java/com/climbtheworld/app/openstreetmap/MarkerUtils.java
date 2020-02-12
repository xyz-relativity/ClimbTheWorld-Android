package com.climbtheworld.app.openstreetmap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import java.util.HashMap;
import java.util.Map;

public class MarkerUtils {
    private static final int originalW = 200;
    private static final int originalH = 270;
    private static final String UNKNOWN_TYPE = "-?-";

    private static final Map<String, Drawable> iconCache = new HashMap<>();

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        return getPoiIcon(parent, 1, poi, MarkerGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, int alpha) {
        return getPoiIcon(parent, 1, poi, alpha);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, double sizeFactor, GeoNode poi) {
        return getPoiIcon(parent, sizeFactor, poi, MarkerGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, double sizeFactor, GeoNode poi, int alpha) {
        String gradeValue = GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG));
        String mapKey = gradeValue + "|" + sizeFactor + "|" + poi.getNodeType() + "|" + alpha;

        if (!iconCache.containsKey(mapKey)) {
            addNodeToCache(parent, poi, sizeFactor, alpha, mapKey, gradeValue);
        }

        return iconCache.get(mapKey);
    }

    private static synchronized void addNodeToCache(AppCompatActivity parent, GeoNode poi, double sizeFactor, int alpha, String mapKey, String gradeValue) {
        if (!iconCache.containsKey(mapKey)) {
            Drawable nodeIcon;
            Bitmap bitmap;
            switch (poi.getNodeType()) {
                case crag:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_crag);
                    bitmap = getBitmap(nodeIcon, originalW, originalH, sizeFactor);
                    break;

                case artificial:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_artificial);
                    bitmap = getBitmap(nodeIcon, originalW, originalH, sizeFactor);
                    break;

                case route:
                    bitmap = createBitmapFromLayout(parent, sizeFactor, gradeValue,
                                    Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG)));
                    break;

                case unknown:
                default:
                    bitmap = createBitmapFromLayout(parent, sizeFactor, UNKNOWN_TYPE,
                                            ColorStateList.valueOf(MarkerGeoNode.POI_DEFAULT_COLOR).withAlpha(255));
                    break;
            }
            iconCache.put(mapKey, toBitmapDrawableAlpha(parent, bitmap, alpha));
        }
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
    };

    private static Bitmap createBitmapFromLayout (AppCompatActivity parent, double sizeFactor, String gradeValue, ColorStateList color) {
        int heightC = Math.round(Globals.sizeToDPI(parent, originalH));
        int widthC = Math.round(Globals.sizeToDPI(parent, originalW));

        LayoutInflater inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newViewElement = inflater.inflate(R.layout.icon_topo_display, null);

        ((TextView) newViewElement.findViewById(R.id.textPinGrade)).setText(gradeValue);
        ((ImageView) newViewElement.findViewById(R.id.imagePinGrade)).setImageTintList(color);

        final int height = View.MeasureSpec.makeMeasureSpec(heightC, View.MeasureSpec.EXACTLY);
        final int width = View.MeasureSpec.makeMeasureSpec(widthC, View.MeasureSpec.EXACTLY);
        newViewElement.measure(width, height);
        newViewElement.layout(0, 0, newViewElement.getMeasuredWidth(), newViewElement.getMeasuredHeight());

        newViewElement.setDrawingCacheEnabled(true);
        newViewElement.buildDrawingCache(true);
        Bitmap result = Bitmap.createScaledBitmap(newViewElement.getDrawingCache(),
                        (int)Math.round(originalW * sizeFactor),
                        (int)Math.round(originalH * sizeFactor), true);

        newViewElement.setDrawingCacheEnabled(false);

        return result;
    }

    public static Bitmap getBitmap(Drawable vectorDrawable, int imgW, int imgH, double sizeFactor) {
        Bitmap bitmap = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0,
                canvas.getWidth(),
                canvas.getHeight());
        vectorDrawable.draw(canvas);
        return Bitmap.createScaledBitmap(bitmap,
                (int)Math.round(imgW * sizeFactor),
                (int)Math.round(imgH * sizeFactor), true);
    }

    public static class SpinnerMarkerArrayAdapter extends ArrayAdapter<GeoNode.NodeTypes> {

        private LayoutInflater inflater;
        AppCompatActivity context;
        GeoNode editPoi;

        public SpinnerMarkerArrayAdapter(AppCompatActivity context, int resource, GeoNode.NodeTypes[] objects, GeoNode poi) {
            super(context, resource, objects);
            this.context = context;
            this.inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
            this.editPoi = poi;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent, true);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent, false);
        }

        private View getCustomView(int position, View convertView, ViewGroup parent, boolean selected) {
            View v = inflater.inflate(R.layout.list_item_switch_description, null);
            v.findViewById(R.id.layoutSwitch).setVisibility(View.GONE);
            TextView textView = v.findViewById(R.id.textTypeName);
            textView.setText(getItem(position).getNameId());
            textView = v.findViewById(R.id.textTypeDescription);
            textView.setText(getItem(position).getDescriptionId());
            GeoNode poi = new GeoNode(0, 0, 0);
            poi.setNodeType(getItem(position));
            poi.setLevelFromID(editPoi.getLevelId(GeoNode.KEY_GRADE_TAG), GeoNode.KEY_GRADE_TAG);
            ImageView imageView = v.findViewById(R.id.imageIcon);
            imageView.setImageBitmap(((BitmapDrawable)getPoiIcon(context, MarkerGeoNode.POI_ICON_SIZE_MULTIPLIER, poi)).getBitmap());

            if (selected && editPoi.getNodeType() == getItem(position)) {
                v.setBackgroundColor(Color.parseColor("#eecccccc"));
            }
            return v;
        }
    }
}
