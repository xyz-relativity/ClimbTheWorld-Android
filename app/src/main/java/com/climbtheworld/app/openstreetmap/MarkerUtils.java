package com.climbtheworld.app.openstreetmap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
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
    private final static float scale = Resources.getSystem().getDisplayMetrics().density;

    public enum IconType {
        poiIcon(200, 270, (int)Math.round(64 * 0.74), 64),
        poiCLuster(300, 300, 64, 64);

        public int originalW;
        public int originalH;
        public int dpW;
        public int dpH;

        private IconType(int originW, int originH, int dpW, int dpH) {
            this.originalW = originW;
            this.originalH = originH;
            this.dpW = dpW;
            this.dpH = dpH;
        }

        public int getPixelW() {
            return (int) (dpW * scale + 0.5f);
        }

        public int getPixelH() {
            return (int) (dpH * scale + 0.5f);
        }
    }
    private static final String UNKNOWN_TYPE = "-?-";

    private static final Map<String, Drawable> iconCache = new HashMap<>();

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi) {
        return getPoiIcon(parent, poi, MarkerGeoNode.POI_ICON_ALPHA_VISIBLE);
    }

    public static Drawable getPoiIcon(AppCompatActivity parent, GeoNode poi, int alpha) {
        String gradeValue = GradeSystem.fromString(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.getLevelId(GeoNode.KEY_GRADE_TAG));
        String mapKey = gradeValue + "|" + poi.getNodeType() + "|" + alpha;

        if (!iconCache.containsKey(mapKey)) {
            addNodeToCache(parent, poi, alpha, mapKey, gradeValue);
        }

        return iconCache.get(mapKey);
    }

    private static synchronized void addNodeToCache(AppCompatActivity parent, GeoNode poi, int alpha, String mapKey, String gradeValue) {
        if (!iconCache.containsKey(mapKey)) {
            Drawable nodeIcon;
            Bitmap bitmap;
            switch (poi.getNodeType()) {
                case crag:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_crag);
                    bitmap = getBitmap(parent, nodeIcon, IconType.poiIcon);
                    break;

                case artificial:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_artificial);
                    bitmap = getBitmap(parent, nodeIcon, IconType.poiIcon);
                    break;

                case route:
                    bitmap = createBitmapFromLayout(parent, gradeValue,
                                    Globals.gradeToColorState(poi.getLevelId(GeoNode.KEY_GRADE_TAG)), IconType.poiIcon);
                    break;

                case unknown:
                default:
                    bitmap = createBitmapFromLayout(parent, UNKNOWN_TYPE,
                                            ColorStateList.valueOf(MarkerGeoNode.POI_DEFAULT_COLOR).withAlpha(255), IconType.poiIcon);
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

    private static Bitmap createBitmapFromLayout (AppCompatActivity parent, String gradeValue, ColorStateList color, IconType iconType) {
        int heightC = Math.round(Globals.sizeToDPI(parent, iconType.originalH));
        int widthC = Math.round(Globals.sizeToDPI(parent, iconType.originalW));

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
        Bitmap result = Bitmap.createScaledBitmap(newViewElement.getDrawingCache(), iconType.getPixelW(), iconType.getPixelH(), true);

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
        return Bitmap.createScaledBitmap(bitmap, iconType.getPixelW(), iconType.getPixelH(), true);
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
            imageView.setImageBitmap(((BitmapDrawable)getPoiIcon(context, poi)).getBitmap());

            if (selected && editPoi.getNodeType() == getItem(position)) {
                v.setBackgroundColor(Color.parseColor("#eecccccc"));
            }
            return v;
        }
    }
}
