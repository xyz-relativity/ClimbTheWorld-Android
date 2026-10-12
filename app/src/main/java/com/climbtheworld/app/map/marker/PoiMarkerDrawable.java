package com.climbtheworld.app.map.marker;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class PoiMarkerDrawable extends Drawable {
	/**
	 * The look of route pins. The augmented reality view uses slim ones, so that the routes along
	 * a cliff overlap less, and everything else the regular ones. Crag, area and gym pins look the
	 * same with both.
	 */
	public enum PinShape {
		regular,
		slim
	}

	final WeakReference<AppCompatActivity> parentRef;
	final DisplayableGeoNode poi;
	private final boolean slim;
	private final boolean routeTop;
	private String gradeString;
	private float gradeBaseline;
	private Paint backgroundPaint;
	private int IntrinsicWidth;
	private int IntrinsicHeight;
	private TextPaint gradeTextPaint;
	private Paint styleIconPaint;
	private Bitmap originalBitmap;
	private int centerX;
	private TextPaint nameTextPaint;
	private TextPaint routeCountTextPaint;
	private String routeCountString;
	private ArrayList<String> nameSplit;
	private ColorStateList color;
	ColorFilter colorFilter = null;
	int alpha;
	private final float scale = 1;

	private final Semaphore refreshLock = new Semaphore(1);
	private boolean isRendererPrepared = false;

	//positioning constants:
	private final static int TEXT_PADDING = Globals.convertDpToPixel(0).intValue();

	private final static int GRADE_TOP_OFFSET = Globals.convertDpToPixel(24).intValue();
	private final static int GRADE_HORIZONTAL_MARGIN = Globals.convertDpToPixel(14).intValue();
	private final static float GRADE_FONT_SIZE = Globals.convertDpToPixel(18).floatValue();
	private final static float GRADE_OUTLINE_STRENGTH = Globals.convertDpToPixel(3).floatValue();

	private final static int NAME_TOP_OFFSET = Globals.convertDpToPixel(35).intValue();
	// Crag/area pins have the type glyph at the top of the big circle and the route count in the
	// second circle, so the name sits just under the glyph.
	private final static int GROUP_NAME_TOP_OFFSET = Globals.convertDpToPixel(27).intValue();
	private final static float GROUP_STYLE_ICON_GAP = Globals.convertDpToPixel(2).floatValue();
	private final static float GROUP_STYLE_ICON_BOTTOM_MARGIN = Globals.convertDpToPixel(1).floatValue();
	private final static float ROUTE_COUNT_CIRCLE_PADDING = Globals.convertDpToPixel(2).floatValue();

	// ic_poi_info geometry, in its vector viewport units.
	private final static float PIN_VIEWPORT_WIDTH = 33.86667f;
	private final static float PIN_VIEWPORT_HEIGHT = 52.916664f;
	private final static float PIN_BIG_CIRCLE_BOTTOM = 17.3249f + 16.6627f;
	private final static float PIN_SECOND_CIRCLE_CENTER_Y = 39.5418f;
	private final static float PIN_SECOND_CIRCLE_RADIUS_X = 5.4242f;
	private final static int[] NAME_HORIZONTAL_MARGIN = new int[]{
			Globals.convertDpToPixel(8).intValue(), //First line
			Globals.convertDpToPixel(24).intValue() //Second line
	};
	private final static float NAME_FONT_SIZE = Globals.convertDpToPixel(9).floatValue();
	private final static float NAME_OUTLINE_STRENGTH =
			Globals.convertDpToPixel(DisplayableGeoNode.MARKER_TEXT_OUTLINE_DP).floatValue();
	private final static float ROUTE_COUNT_FONT_SIZE = Globals.convertDpToPixel(9).floatValue();
	private final static float ROUTE_COUNT_OUTLINE_STRENGTH =
			Globals.convertDpToPixel(DisplayableGeoNode.MARKER_TEXT_OUTLINE_DP).floatValue();

	private final static int STYLE_TOP_OFFSET = Globals.convertDpToPixel(57).intValue();
	private final static float STYLE_ICON_SIZE = Globals.convertDpToPixel(10).floatValue();

	// ic_poi_route and ic_poi_route_top geometry; their viewport is in dp.
	private final static float SLIM_HEAD_CENTER_Y = Globals.convertDpToPixel(12).floatValue();
	private final static float SLIM_GRADE_MAX_WIDTH = Globals.convertDpToPixel(19).floatValue();
	private final static float SLIM_NAME_TOP = Globals.convertDpToPixel(25).floatValue();
	private final static float SLIM_NAME_BOTTOM = Globals.convertDpToPixel(72).floatValue();
	private final static float SLIM_STYLE_CENTER_Y = Globals.convertDpToPixel(79).floatValue();
	// Narrower letters fit more of the grade in the head and of the name along the body.
	private final static String SLIM_FONT_FAMILY = "sans-serif-condensed";

	Runnable backendRunnable = () -> {
		prepareForRender();
		refreshLock.release();
	};
	private Bitmap styleIcon;

	public PoiMarkerDrawable(AppCompatActivity parent, DisplayableGeoNode poi) {
		this(parent, poi, poi.getAlpha());
	}

	public PoiMarkerDrawable(AppCompatActivity parent, DisplayableGeoNode poi, int alpha) {
		this(parent, poi, alpha, PinShape.regular);
	}

	public PoiMarkerDrawable(AppCompatActivity parent, DisplayableGeoNode poi, int alpha,
	                         PinShape shape) {
		super();
		this.parentRef = new WeakReference<>(parent);
		this.poi = poi;
		this.alpha = alpha;
		GeoNode.NodeTypes nodeType = poi.getGeoNode().getNodeType();
		this.slim = shape == PinShape.slim
				&& (nodeType == GeoNode.NodeTypes.route || nodeType == GeoNode.NodeTypes.unknown);
		this.routeTop = slim && poi.getGeoNode().isRouteTop();

		prepareBackground(parentRef.get(), poi);
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		renderDrawable(canvas, 0, 0, scale);
	}

	private void renderDrawable(Canvas canvas, int offsetX, int offsetY, float scale) {
		if (isRendererPrepared) {
			canvas.save();
			canvas.translate(offsetX, offsetY);
			canvas.scale(scale, scale, originalBitmap.getWidth()/2f,  originalBitmap.getHeight());
			//draw background
			canvas.drawBitmap(originalBitmap, 0, 0, backgroundPaint);

			//draw grade text
			//outline
			this.gradeTextPaint.setStyle(Paint.Style.STROKE);
			this.gradeTextPaint.setStrokeWidth(GRADE_OUTLINE_STRENGTH);
			this.gradeTextPaint.setColor(Color.WHITE);
			this.gradeTextPaint.setAlpha(alpha);
			canvas.drawText(gradeString, 0, gradeString.length(), centerX, gradeBaseline, gradeTextPaint);

			//text
			this.gradeTextPaint.setStyle(Paint.Style.FILL);
			this.gradeTextPaint.setColor(Color.BLACK);
			this.gradeTextPaint.setAlpha(alpha);
			canvas.drawText(gradeString, 0, gradeString.length(), centerX, gradeBaseline, gradeTextPaint);

			//draw name text
			if (slim) {
				drawSlimName(canvas);
			} else {
				for (int i = 0; i < nameSplit.size(); ++i) {
					drawNameLine(canvas, nameSplit.get(i), centerX, getNameBaseline(i));
				}
			}

			if (!routeCountString.isEmpty()) {
				float routeCountBaseline = getRouteCountBaseline();
				routeCountTextPaint.setStyle(Paint.Style.STROKE);
				routeCountTextPaint.setStrokeWidth(ROUTE_COUNT_OUTLINE_STRENGTH);
				routeCountTextPaint.setColor(Color.WHITE);
				routeCountTextPaint.setAlpha(alpha);
				canvas.drawText(routeCountString, centerX, routeCountBaseline,
						routeCountTextPaint);
				routeCountTextPaint.setStyle(Paint.Style.FILL);
				routeCountTextPaint.setColor(Color.BLACK);
				routeCountTextPaint.setAlpha(alpha);
				canvas.drawText(routeCountString, centerX, routeCountBaseline,
						routeCountTextPaint);
			}

			if (styleIcon != null) {
				float styleCenterY;
				if (slim) {
					styleCenterY = SLIM_STYLE_CENTER_Y;
				} else if (isGroupIcon()) {
					styleCenterY = getGroupStyleIconCenter();
				} else {
					styleCenterY = STYLE_TOP_OFFSET;
				}
				canvas.drawBitmap(styleIcon, centerX - (styleIcon.getWidth() / 2f),
						styleCenterY - (styleIcon.getHeight() / 2f), styleIconPaint);
			}

			//done
			canvas.restore();
		} else {
			prepareForRender(true);
		}
	}

	private void drawNameLine(Canvas canvas, String line, float x, float baseline) {
		//outline
		this.nameTextPaint.setStyle(Paint.Style.STROKE);
		this.nameTextPaint.setStrokeWidth(NAME_OUTLINE_STRENGTH);
		this.nameTextPaint.setColor(Color.WHITE);
		this.nameTextPaint.setAlpha(alpha);
		canvas.drawText(line, 0, line.length(), x, baseline, nameTextPaint);

		//text
		this.nameTextPaint.setStyle(Paint.Style.FILL);
		this.nameTextPaint.setColor(Color.BLACK);
		this.nameTextPaint.setAlpha(alpha);
		canvas.drawText(line, 0, line.length(), x, baseline, nameTextPaint);
	}

	// The name runs down the body, reading top to bottom, so it starts next to the grade.
	private void drawSlimName(Canvas canvas) {
		if (nameSplit.isEmpty()) {
			return;
		}
		Paint.FontMetrics metrics = nameTextPaint.getFontMetrics();
		canvas.save();
		canvas.translate(centerX, SLIM_NAME_TOP);
		canvas.rotate(90);
		drawNameLine(canvas, nameSplit.get(0), 0, -(metrics.ascent + metrics.descent) / 2f);
		canvas.restore();
	}

	private void prepareForRender(boolean async) {
		if (async && refreshLock.tryAcquire()) {
			Constants.ASYNC_TASK_EXECUTOR.execute(backendRunnable);
		} else {
			backendRunnable.run();
		}
	}

	private void prepareForRender() {
		prepareBackground(parentRef.get(), poi);
		prepareGradeText();
		prepareNameText();
		prepareRouteCountText();
		prepareStyleRender();
	}

	private boolean isGroupIcon() {
		GeoNode.NodeTypes nodeType = poi.getGeoNode().getNodeType();
		return nodeType == GeoNode.NodeTypes.area || nodeType == GeoNode.NodeTypes.crag;
	}

	private float getNameBaseline(int line) {
		int topOffset = isGroupIcon() ? GROUP_NAME_TOP_OFFSET : NAME_TOP_OFFSET;
		return topOffset + NAME_FONT_SIZE * line + TEXT_PADDING * line;
	}

	// Below the name, but kept inside the big circle.
	private float getGroupStyleIconCenter() {
		float belowName = getNameBaseline(Math.max(nameSplit.size(), 1) - 1)
				+ GROUP_STYLE_ICON_GAP + STYLE_ICON_SIZE / 2;
		float bigCircleBottom = IntrinsicHeight * PIN_BIG_CIRCLE_BOTTOM / PIN_VIEWPORT_HEIGHT;
		return Math.min(belowName, bigCircleBottom - GROUP_STYLE_ICON_BOTTOM_MARGIN - STYLE_ICON_SIZE / 2);
	}

	// Vertically centred in the pin's second circle.
	private float getRouteCountBaseline() {
		float circleCenterY = IntrinsicHeight * PIN_SECOND_CIRCLE_CENTER_Y / PIN_VIEWPORT_HEIGHT;
		Paint.FontMetrics metrics = routeCountTextPaint.getFontMetrics();
		return circleCenterY - (metrics.ascent + metrics.descent) / 2f;
	}

	private void prepareRouteCountText() {
		routeCountString = isGroupIcon() ? poi.getGeoNode().getKey(ClimbingTags.KEY_ROUTES) : "";
		if (routeCountString == null) {
			routeCountString = "";
		}
		routeCountTextPaint = new TextPaint();
		routeCountTextPaint.setAntiAlias(true);
		routeCountTextPaint.setSubpixelText(true);
		routeCountTextPaint.setTextSize(ROUTE_COUNT_FONT_SIZE);
		routeCountTextPaint.setTextAlign(Paint.Align.CENTER);
		routeCountTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		routeCountTextPaint.setAlpha(alpha);

		// Shrink long counts so they stay inside the second circle.
		if (!routeCountString.isEmpty()) {
			float circleWidth = 2f * IntrinsicWidth * PIN_SECOND_CIRCLE_RADIUS_X / PIN_VIEWPORT_WIDTH;
			float availableWidth = circleWidth - 2f * ROUTE_COUNT_CIRCLE_PADDING;
			float textWidth = routeCountTextPaint.measureText(routeCountString);
			if (textWidth > availableWidth && textWidth > 0) {
				routeCountTextPaint.setTextSize(ROUTE_COUNT_FONT_SIZE * availableWidth / textWidth);
			}
		}
	}

	private void prepareStyleRender() {
		if ((poi.getGeoNode().getNodeType() != GeoNode.NodeTypes.route
				&& poi.getGeoNode().getNodeType() != GeoNode.NodeTypes.crag) || routeTop) {
			return;
		}

		this.styleIconPaint = new Paint();
		this.styleIconPaint.setAntiAlias(true);
		this.styleIconPaint.setFilterBitmap(true);
		this.styleIconPaint.setDither(true);
		this.styleIconPaint.setAlpha(alpha);
		this.styleIconPaint.setTextAlign(Paint.Align.CENTER);

		styleIcon = ((BitmapDrawable) MarkerUtils.getStyleIcon(parentRef.get(), poi.geoNode.getClimbingStyles(), (int) STYLE_ICON_SIZE)).getBitmap();
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
			color = Globals.gradeToColorState(poi.geoNode.getLevelId(ClimbingTags.KEY_GRADE_TAG));
		} else if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.area
				|| poi.geoNode.getNodeType() == GeoNode.NodeTypes.crag) {
			color = ColorStateList.valueOf(
					DisplayableGeoNode.getHullPinColor(poi.geoNode.getNodeType()));
		}

		Drawable drawable = slim
				? MarkerUtils.getSlimRouteIcon(parent, color, routeTop)
				: MarkerUtils.getPoiIcon(parent, poi.geoNode, color);
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
		this.nameTextPaint.setTypeface(slim ? Typeface.create(SLIM_FONT_FAMILY, Typeface.BOLD)
				: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
		this.nameTextPaint.setLetterSpacing(-0.05f);
		this.nameTextPaint.setTextSize(NAME_FONT_SIZE);
		this.nameTextPaint.setTextAlign(slim ? Paint.Align.LEFT : Paint.Align.CENTER);
		this.nameTextPaint.setLinearText(true);

		// The route top marker leaves the name to the route bottom pin.
		String name = routeTop ? "" : poi.geoNode.getName();
		if (!routeTop && poi.getRouteNumber() > 0) {
			name = poi.getRouteNumber() + "." + name;
		}
		this.nameSplit = new ArrayList<>();
		if (slim) {
			// A single line along the body, cut short if need be.
			if (!name.isEmpty()) {
				nameSplit.add(TextUtils.ellipsize(name, nameTextPaint,
						SLIM_NAME_BOTTOM - SLIM_NAME_TOP, TextUtils.TruncateAt.END).toString());
			}
		} else if (!name.isEmpty()) {
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
		this.gradeTextPaint.setTypeface(slim ? Typeface.create(SLIM_FONT_FAMILY, Typeface.BOLD)
				: Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
		this.gradeTextPaint.setLetterSpacing(-0.05f);
		this.gradeTextPaint.setTextSize(GRADE_FONT_SIZE);
		this.gradeTextPaint.setTextAlign(Paint.Align.CENTER);
		this.gradeTextPaint.setLinearText(true);
		this.gradeTextPaint.getTextBounds(gradeString, 0, gradeString.length(), rect);
		float maxWidth = slim ? SLIM_GRADE_MAX_WIDTH : IntrinsicWidth - GRADE_HORIZONTAL_MARGIN;
		float sizeW = Math.round(maxWidth) * GRADE_FONT_SIZE / (float) (rect.width());
		this.gradeTextPaint.setTextSize(Math.min(sizeW, GRADE_FONT_SIZE));

		if (slim) {
			// Centred on the drawn grade rather than on the font metrics, so that it sits in the
			// middle of the head whatever its size.
			this.gradeTextPaint.getTextBounds(gradeString, 0, gradeString.length(), rect);
			this.gradeBaseline = SLIM_HEAD_CENTER_Y - rect.exactCenterY();
		} else {
			this.gradeBaseline = GRADE_TOP_OFFSET;
		}
	}

	private String getGradeString() {
		if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.unknown) {
			return MarkerUtils.UNKNOWN_TYPE;
		} else if (poi.geoNode.getNodeType() == GeoNode.NodeTypes.route) {
			return GradeSystem.fromString(Configs.instance(parentRef).getString(Configs.ConfigKey.usedGradeSystem)).getGrade(poi.geoNode.getLevelId(ClimbingTags.KEY_GRADE_TAG));
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
		renderDrawable(canvas, 0, 0, 1);
		return new BitmapDrawable(parentRef.get().getResources(), newBitmap);
	}
}
