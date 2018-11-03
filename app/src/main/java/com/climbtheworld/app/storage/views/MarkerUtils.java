package com.climbtheworld.app.storage.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeConverter;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.util.HashMap;
import java.util.Map;

public class MarkerUtils {
    private static int originalW = 196;
    private static int originalH = 300;

    private static Map<String, Bitmap> iconCache = new HashMap<>();

    public static Bitmap getPoiIcon(Context parent, GeoNode poi) {
        return getPoiIcon(parent, poi, 1);
    }

    public static Bitmap getPoiIcon(Context parent, GeoNode poi, double sizeFactor) {
        return getPoiIcon(parent, poi, sizeFactor, 210);
    }

    public static Bitmap getPoiIcon(Context parent, GeoNode poi, double sizeFactor, int alpha) {
        String gradeValue = GradeConverter.getConverter().
                getGradeFromOrder(Globals.globalConfigs.getString(Configs.ConfigKey.usedGradeSystem), poi.getLevelId());
        String mapKey = gradeValue + "|" + sizeFactor + "|" + poi.nodeType;

        if (!iconCache.containsKey(mapKey)) {

            Drawable nodeIcon;
            switch (poi.nodeType) {
                case crag:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_crag);
                    iconCache.put(mapKey,
                            getBitmap((VectorDrawable) nodeIcon, originalW, originalH, sizeFactor));
                    break;

                case artificial:
                    nodeIcon = parent.getResources().getDrawable(R.drawable.ic_poi_gym);
                    iconCache.put(mapKey,
                            getBitmap((VectorDrawable) nodeIcon, originalW, originalH, sizeFactor));
                    break;

                case route:
                    iconCache.put(mapKey,
                            createBitmapFromLayout (parent, poi, sizeFactor, gradeValue, Globals.gradeToColorState(poi.getLevelId()).withAlpha(alpha)));
                    break;

                case unknown:
                default:
                    iconCache.put(mapKey,
                            createBitmapFromLayout (parent, poi, sizeFactor, "?",
                                    ColorStateList.valueOf(MarkerGeoNode.POI_DEFAULT_COLOR).withAlpha(alpha)));
                    break;
            }


        }

        return iconCache.get(mapKey);
    }

    private static Bitmap createBitmapFromLayout (Context parent, GeoNode poi, double sizeFactor, String gradeValue, ColorStateList color) {
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

    public static Bitmap getBitmap(VectorDrawable vectorDrawable, int imgW, int imgH, double sizeFactor) {
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
        Context context;
        GeoNode editPoi;

        public SpinnerMarkerArrayAdapter(Context context, int resource, GeoNode.NodeTypes[] objects, GeoNode poi) {
            super(context, resource, objects);
            this.context = context;
            this.inflater = (LayoutInflater) context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );
            this.editPoi = poi;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        public View getCustomView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.list_item_node_type, null);
            TextView textView = v.findViewById(R.id.textTypeName);
            textView.setText(getItem(position).stringTypeNameId);
            textView = v.findViewById(R.id.textTypeDescription);
            textView.setText(getItem(position).stringTypeDescriptionId);
            GeoNode poi = new GeoNode(0, 0, 0);
            poi.nodeType = getItem(position);
            poi.setLevelFromID(editPoi.getLevelId());
            ImageView imageView = v.findViewById(R.id.imageIcon);
            imageView.setImageBitmap(getPoiIcon(context, poi, Constants.POI_ICON_SIZE_MULTIPLIER));
            return v;
        }
    }
}
