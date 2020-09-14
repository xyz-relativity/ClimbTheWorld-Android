package com.climbtheworld.app.map.marker;

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
import android.text.TextPaint;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import org.osmdroid.views.MapView;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PoiMarkerDrawable extends Drawable {
    private final MapView mapView;
    final AppCompatActivity parent;
    final DisplayableGeoNode poi;
    private String gradeString;
    private Paint backgroundPaint;
    private int IntrinsicWidth;
    private int IntrinsicHeight;
    private TextPaint gradeTextPaint;
    private Paint styleIconPaint;
    private Bitmap originalBitmap;
    private int centerX;
    private TextPaint nameTextPaint;
    private ArrayList<String> nameSplit;
    private ColorStateList color;
    ColorFilter colorFilter = null;
    int alpha;

    private Semaphore refreshLock = new Semaphore(1);
    private boolean isRendererPrepared = false;

    private final float anchorU;
    private final float anchorV;

    //positioning constants:
    private final static int TEXT_PADDING = Math.round(Globals.convertDpToPixel(0));

    private final static int GRADE_TOP_OFFSET = Math.round(Globals.convertDpToPixel(24));
    private final static int GRADE_HORIZONTAL_MARGIN = Math.round(Globals.convertDpToPixel(14));
    private final static float GRADE_FONT_SIZE = Globals.convertDpToPixel(18);
    private final static float GRADE_OUTLINE_STRENGTH = Globals.convertDpToPixel(3);

    private final static int NAME_TOP_OFFSET = Math.round(Globals.convertDpToPixel(35));
    private final static int[] NAME_HORIZONTAL_MARGIN = new int[]{
            Math.round(Globals.convertDpToPixel(8)), //First line
            Math.round(Globals.convertDpToPixel(24)) //Second line
    };
    private final static float NAME_FONT_SIZE = Globals.convertDpToPixel(9);
    private final static float NAME_OUTLINE_STRENGTH = Globals.convertDpToPixel(2);

    private final static int STYLE_TOP_OFFSET = Math.round(Globals.convertDpToPixel(57));
    private final static float STYLE_ICON_SIZE = Globals.convertDpToPixel(10);

    Runnable backendRunnable = () -> {
        prepareForRender();
        refreshLock.release();
    };
    private Bitmap styleIcon;

    public PoiMarkerDrawable(AppCompatActivity parent, MapView mapView, DisplayableGeoNode poi, float anchorU, float anchorV) {
        this(parent, mapView, poi, anchorU, anchorV, poi.getAlpha());
    }

    public PoiMarkerDrawable(AppCompatActivity parent, MapView mapView, DisplayableGeoNode poi, float anchorU, float anchorV, int alpha) {
        super();
        this.parent = parent;
        this.poi = poi;
        this.alpha = alpha;
        this.mapView = mapView;
        this.anchorU = anchorU;
        this.anchorV = anchorV;

        prepareBackground(parent, poi);
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

    private void renderDrawable(Canvas canvas, int offsetX, int offsetY) {
        if (isRendererPrepared) {
            canvas.save();
            canvas.translate(offsetX, offsetY);
            //draw background
            canvas.drawBitmap(originalBitmap, 0, 0, backgroundPaint);

            //draw grade text
            //outline
            this.gradeTextPaint.setStyle(Paint.Style.STROKE);
            this.gradeTextPaint.setStrokeWidth(GRADE_OUTLINE_STRENGTH);
            this.gradeTextPaint.setColor(Color.WHITE);
            this.gradeTextPaint.setAlpha(alpha);
            canvas.drawText(gradeString, 0, gradeString.length(), centerX, GRADE_TOP_OFFSET, gradeTextPaint);

            //text
            this.gradeTextPaint.setStyle(Paint.Style.FILL);
            this.gradeTextPaint.setColor(Color.BLACK);
            this.gradeTextPaint.setAlpha(alpha);
            canvas.drawText(gradeString, 0, gradeString.length(), centerX, GRADE_TOP_OFFSET, gradeTextPaint);

            //draw name text
            for (int i = 0; i < nameSplit.size(); ++i) {
                //outline
                this.nameTextPaint.setStyle(Paint.Style.STROKE);
                this.nameTextPaint.setStrokeWidth(NAME_OUTLINE_STRENGTH);
                this.nameTextPaint.setColor(Color.WHITE);
                this.nameTextPaint.setAlpha(alpha);
                canvas.drawText(nameSplit.get(i), 0, nameSplit.get(i).length(), centerX, NAME_TOP_OFFSET + NAME_FONT_SIZE * i + TEXT_PADDING * i, nameTextPaint);

                //text
                this.nameTextPaint.setStyle(Paint.Style.FILL);
                this.nameTextPaint.setColor(Color.BLACK);
                this.nameTextPaint.setAlpha(alpha);
                canvas.drawText(nameSplit.get(i), 0, nameSplit.get(i).length(), centerX, NAME_TOP_OFFSET + NAME_FONT_SIZE * i + TEXT_PADDING * i, nameTextPaint);
            }

            if (styleIcon != null) {
                canvas.drawBitmap(styleIcon, centerX - (STYLE_ICON_SIZE / 2), STYLE_TOP_OFFSET - (STYLE_ICON_SIZE / 2), styleIconPaint);
            }

            //done
            canvas.restore();
        } else {
            prepareForRender(true);
        }
    }

    private void prepareForRender(boolean async) {
        if (async && refreshLock.tryAcquire()) {
            Constants.ASYNC_TASK_EXECUTOR.execute(backendRunnable);
        } else {
            backendRunnable.run();
        }
    }

    private void prepareForRender() {
        prepareBackground(parent, poi);
        prepareGradeText();
        prepareNameText();
        prepareStyleRender();
    }

    private void prepareStyleRender() {
        if (poi.getGeoNode().getNodeType() != GeoNode.NodeTypes.route
                && poi.getGeoNode().getNodeType() != GeoNode.NodeTypes.crag) {
            return;
        }

        this.styleIconPaint = new Paint();
        this.styleIconPaint.setAntiAlias(true);
        this.styleIconPaint.setFilterBitmap(true);
        this.styleIconPaint.setDither(true);
        this.styleIconPaint.setAlpha(alpha);
        this.styleIconPaint.setTextAlign(Paint.Align.CENTER);

        styleIcon = ((BitmapDrawable) MarkerUtils.getStyleIcon(parent, poi.geoNode.getClimbingStyles(), (int) STYLE_ICON_SIZE)).getBitmap();
    }

    private void prepareBackground(AppCompatActivity parent, DisplayableGeoNode poi) {
        this.backgroundPaint = new Paint();
        this.backgroundPaint.setAntiAlias(true);
        this.backgroundPaint.setFilterBitmap(true);
        this.backgroundPaint.setDither(true);
        this.backgroundPaint.setAlpha(alpha);
        this.backgroundPaint.setTextAlign(Paint.Align.CENTER);

        this.gradeString = getGradeString();
        this.color = ColorStateList.valueOf(DisplayableGeoNode.POI_DEFAULT_COLOR).withAlpha(255);
        if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.route) {
            color = Globals.gradeToColorState(poi.geoNode.getLevelId(GeoNode.KEY_GRADE_TAG));
        }

        Drawable drawable = MarkerUtils.getPoiIcon(parent, poi.geoNode, color);
        this.IntrinsicWidth = drawable.getIntrinsicWidth();
        this.IntrinsicHeight = drawable.getIntrinsicHeight();
        this.originalBitmap = ((BitmapDrawable) drawable).getBitmap();
        this.centerX = IntrinsicWidth / 2;
    }

    private void prepareNameText() {
        this.nameTextPaint = new TextPaint();
        this.nameTextPaint.setStyle(Paint.Style.FILL);
        this.nameTextPaint.setAntiAlias(true);
        this.nameTextPaint.setSubpixelText(true);
        this.nameTextPaint.setAlpha(alpha);
        this.nameTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        this.nameTextPaint.setLetterSpacing(-0.05f);
        this.nameTextPaint.setTextSize(NAME_FONT_SIZE);
        this.nameTextPaint.setTextAlign(Paint.Align.CENTER);
        this.nameTextPaint.setLinearText(true);

        String name = poi.geoNode.getName();
        this.nameSplit = new ArrayList<>();
        if (!name.isEmpty()) {
            int breakPos = nameTextPaint.breakText(name, true, IntrinsicWidth - NAME_HORIZONTAL_MARGIN[0], null);
            if (breakPos < name.length()) {
                String line1 = name.substring(0, breakPos).trim();
                int spaceIndex = line1.lastIndexOf("-");
                if (spaceIndex != -1 && spaceIndex >= breakPos / 2 && spaceIndex != breakPos) {
                    breakPos = spaceIndex + 1; //include the dash in the first line
                    nameSplit.add(name.substring(0, breakPos).trim());
                } else {
                    spaceIndex = line1.lastIndexOf(" ");
                    if (spaceIndex != -1 && spaceIndex >= breakPos / 2) {
                        breakPos = spaceIndex;
                        nameSplit.add(name.substring(0, breakPos).trim());
                    } else {
                        breakPos = line1.length() - 1; //replace last character with a dash and move it on the next line
                        nameSplit.add(line1.substring(0, breakPos) + "-");
                    }
                }

                String line2 = name.substring(breakPos).trim();
                breakPos = nameTextPaint.breakText(line2, true, IntrinsicWidth - NAME_HORIZONTAL_MARGIN[1], null);
                if (breakPos < line2.length()) {
                    nameSplit.add(line2.substring(0, breakPos - 3).trim() + "...");
                } else {
                    nameSplit.add(line2.substring(0, breakPos));
                }
            } else {
                nameSplit.add(name.substring(0, breakPos));
            }
        }
        isRendererPrepared = true;
    }

    private void prepareGradeText() {
        Rect rect = new Rect();
        this.gradeTextPaint = new TextPaint();
        this.gradeTextPaint.setAntiAlias(true);
        this.gradeTextPaint.setSubpixelText(true);
        this.gradeTextPaint.setFakeBoldText(true);
        this.gradeTextPaint.setAlpha(alpha);
        this.gradeTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        this.gradeTextPaint.setLetterSpacing(-0.05f);
        this.gradeTextPaint.setTextSize(GRADE_FONT_SIZE);
        this.gradeTextPaint.setTextAlign(Paint.Align.CENTER);
        this.gradeTextPaint.setLinearText(true);
        this.gradeTextPaint.getTextBounds(gradeString, 0, gradeString.length(), rect);
        float sizeW = Math.round(IntrinsicWidth - GRADE_HORIZONTAL_MARGIN) * GRADE_FONT_SIZE / (float) (rect.width());
        this.gradeTextPaint.setTextSize(Math.min(sizeW, GRADE_FONT_SIZE));
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
        if (!isRendererPrepared) {
            prepareForRender(false);
        }
        Bitmap newBitmap = Bitmap.createBitmap(getIntrinsicWidth(), getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        renderDrawable(canvas, 0, 0);
        return new BitmapDrawable(parent.getResources(), newBitmap);
    }
}
