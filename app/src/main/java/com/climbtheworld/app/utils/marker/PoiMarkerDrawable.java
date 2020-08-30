package com.climbtheworld.app.utils.marker;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import com.climbtheworld.app.openstreetmap.ui.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.tools.GradeSystem;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.views.MapView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PoiMarkerDrawable extends Drawable {
    private final MapView mapView;
    final AppCompatActivity parent;
    final DisplayableGeoNode poi;
    private final String gradeString;
    private final Paint backgropundPaint;
    private final int IntrinsicWidth;
    private final int IntrinsicHeight;
    private final TextPaint gradeTextPaint;
    private final Bitmap originalBitmap;
    private ColorStateList color;
    ColorFilter colorFilter = null;
    int alpha;
    private final float anchorU;
    private final float anchorV;

    //positioning constants:
    private final static int GRADE_TOP_OFFSET = Math.round(Globals.convertDpToPixel(20));
    private final static float GRADE_FONT_SIZE = Globals.convertDpToPixel(16);

    private final static int NAME_TOP_OFFSET = Math.round(GRADE_TOP_OFFSET + GRADE_FONT_SIZE + Globals.convertDpToPixel(2));
    private final static float NAME_FONT_SIZE = Globals.convertDpToPixel(12);

    public PoiMarkerDrawable(AppCompatActivity parent, MapView mapView, DisplayableGeoNode poi, float anchorU, float anchorV) {
        super();
        this.parent = parent;
        this.poi = poi;
        this.alpha = poi.getAlpha();
        this.mapView = mapView;
        this.anchorU = anchorU;
        this.anchorV = anchorV;

        this.backgropundPaint = new Paint();
        this.backgropundPaint.setAntiAlias(true);
        this.backgropundPaint.setFilterBitmap(true);
        this.backgropundPaint.setDither(true);
        this.backgropundPaint.setAlpha(alpha);
        this.backgropundPaint.setTextAlign(Paint.Align.CENTER);

        this.gradeString = getGradeString();
        this.color = ColorStateList.valueOf(DisplayableGeoNode.POI_DEFAULT_COLOR).withAlpha(255);
        if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.route) {
            color = Globals.gradeToColorState(poi.geoNode.getLevelId(GeoNode.KEY_GRADE_TAG));
        }

        Drawable drawable = MarkerUtils.getPoiIcon(parent, poi.geoNode, color);
        this.IntrinsicWidth = drawable.getIntrinsicWidth();
        this.IntrinsicHeight = drawable.getIntrinsicHeight();
        this.originalBitmap = ((BitmapDrawable)drawable).getBitmap();

        Rect rect = new Rect();
        this.gradeTextPaint = new TextPaint();//The Paint that will draw the text
        this.gradeTextPaint.setStyle(Paint.Style.FILL);
        this.gradeTextPaint.setAntiAlias(true);
        this.gradeTextPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        this.gradeTextPaint.setShadowLayer(1f, 0f, 3f, Color.WHITE);
        this.gradeTextPaint.setTextSize(GRADE_FONT_SIZE);
        this.gradeTextPaint.setTextAlign(Paint.Align.CENTER);
        this.gradeTextPaint.setLinearText(true);
        this.gradeTextPaint.getTextBounds(gradeString, 0, gradeString.length(), rect);
        float sizeW = Math.round((IntrinsicWidth - Globals.convertDpToPixel(11))) * GRADE_FONT_SIZE / (float) (rect.width());
        this.gradeTextPaint.setTextSize( Math.min(sizeW, GRADE_FONT_SIZE));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        int offsetX = 0;
        int offsetY = 0;

        if (mapView != null) {
            Point mPositionPixels = new Point();
            mapView.getProjection().toPixels(Globals.poiToGeoPoint(poi.geoNode), mPositionPixels);
            offsetX = mPositionPixels.x - Math.round(getIntrinsicWidth() * anchorU);
            offsetY = mPositionPixels.y - Math.round(getIntrinsicHeight() * anchorV);
        }

        renderDrawable(canvas, offsetX, offsetY);
    }

    private void renderDrawable(Canvas canvas,  int offsetX, int offsetY) {
        canvas.save();
        canvas.translate(offsetX, offsetY);

        //draw background
        canvas.drawBitmap(originalBitmap, 0, 0, backgropundPaint);

        //draw grade text
        int centerX = (originalBitmap.getWidth() / 2);
        canvas.drawText(gradeString, 0, gradeString.length(), centerX, GRADE_TOP_OFFSET, gradeTextPaint);

//        int centerX = (originalBitmap.getWidth() / 2);
//        float horizontalMargin = Globals.convertDpToPixel(10);
//
//        //draw grade
//        textEllipsize(canvas, gradeString, GRADE_FONT_SIZE, centerX, GRADE_TOP_OFFSET);
//        Rect rect = new Rect();
//        paint.setTextSize(GRADE_FONT_SIZE);
//        paint.getTextBounds(gradeString, 0, gradeString.length(), rect);
//        float sizeW = Math.round((originalBitmap.getWidth() - horizontalMargin)) * GRADE_FONT_SIZE / (float) (rect.width());
//        drawMultilineTextToCanvas(canvas, getGradeString(), Math.min(sizeW, GRADE_FONT_SIZE), Math.round(originalBitmap.getWidth() - horizontalMargin), centerX, GRADE_TOP_OFFSET);
//
//        //draw name
        drawMultilineTextToCanvas(canvas, poi.geoNode.getName(), NAME_FONT_SIZE, Math.round(originalBitmap.getWidth()), centerX, NAME_TOP_OFFSET);
//        textEllipsize(canvas, poi.geoNode.getName(), NAME_FONT_SIZE, centerX, NAME_TOP_OFFSET);

        //done
        canvas.restore();
    }

    private void textEllipsize(Canvas canvas, String gText, float textSize, int x, int y) {
        TextPaint textPaint = new TextPaint();//The Paint that will draw the text
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        textPaint.setShadowLayer(1f, 0f, 3f, Color.WHITE);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setLinearText(true);

        Rect b = getBounds(); //The dimensions of your canvas
        int width = b.width() - 10; //10 to keep some space on the right for the "..."
        CharSequence txt = TextUtils.ellipsize(gText, textPaint, width, TextUtils.TruncateAt.END, false, new TextUtils.EllipsizeCallback() {
            @Override
            public void ellipsized(int i, int i1) {
                System.out.println(i + " " + i1);
            }
        });
        canvas.drawText(txt, 0, txt.length(), x, y, textPaint);
    }

    private void drawMultilineTextToCanvas(Canvas canvas, String gText, float textSize, int textWidth, int x, int y) {
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        paint.setShadowLayer(1f, 0f, 3f, Color.WHITE);

        StaticLayout textLayout = new StaticLayout(
                gText, paint, textWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        canvas.save();
        canvas.translate(x - (int)(textLayout.getWidth() / 2), y);
        textLayout.draw(canvas);
        canvas.restore();
    }

    private String getGradeString() {
        if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.unknown) {
            return MarkerUtils.UNKNOWN_TYPE;
        } else if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.route) {
            return GradeSystem.fromString(Configs.instance(parent).getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.geoNode.getLevelId(GeoNode.KEY_GRADE_TAG));
        }

        return "";
    }

    @Override
    public int getIntrinsicWidth() {
        return IntrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return IntrinsicHeight;
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

    public Drawable getDrawable() {
        Bitmap newBitmap = Bitmap.createBitmap(getIntrinsicWidth(), getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        renderDrawable(canvas, 0, 0);
        return new BitmapDrawable(parent.getResources(), newBitmap);
    }

    private String getNodeStyleString() {
        StringBuilder result = new StringBuilder();
        for (GeoNode.ClimbingStyle type : poi.geoNode.getClimbingStyles()) {
            result.append(type.getIconResource());
        }

        return result.toString();
    }
}
