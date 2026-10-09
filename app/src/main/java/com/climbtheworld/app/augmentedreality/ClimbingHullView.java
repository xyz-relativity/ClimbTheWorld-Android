package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.sensors.camera.VirtualCamera;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.Vector2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the {@link ClimbingHulls} on the ground as the virtual camera sees them, in the colours of
 * the map. Up close, where the routes take the place of the pin, the hull is labelled with its
 * name instead. It sits in the AR container, which is centred on the camera view and turned with
 * the device roll.
 */
public class ClimbingHullView extends View {
	// Light, so the camera view stays clear.
	private static final int FILL_ALPHA = 0x30;
	private static final int LINE_ALPHA = 0xe0;
	// A dark edge along the line keeps it readable over bright rock and sky.
	private static final int CASING_COLOR = Color.argb(0x80, 0, 0, 0);
	private static final float LINE_WIDTH_DP = 3;
	private static final float CASING_WIDTH_DP = 5;
	private static final int LABEL_ALPHA = 0xe0;
	private static final float LABEL_TEXT_SP = 12;
	private static final float LABEL_PADDING_X_DP = 6;
	private static final float LABEL_PADDING_Y_DP = 2;
	private static final float LABEL_RADIUS_DP = 6;
	private static final float LABEL_BORDER_DP = 1;
	// Between the outline and the label hanging under it.
	private static final float LABEL_GAP_DP = 4;
	private static final float LABEL_MARGIN_DP = 8;
	// Clear of the compass at the bottom of the view.
	private static final float LABEL_BOTTOM_MARGIN_DP = 88;
	private static final float LABEL_MAX_WIDTH_DP = 240;
	private final HullProjection projection = new HullProjection();
	private final Path fillPath = new Path();
	private final Paint areaFillPaint;
	private final Paint cragFillPaint;
	private final Paint areaLinePaint;
	private final Paint cragLinePaint;
	private final Paint casingPaint;
	private final TextPaint labelTextPaint;
	private final Paint areaLabelPaint;
	private final Paint cragLabelPaint;
	private final Paint labelBorderPaint;
	private final float labelPaddingX;
	private final float labelPaddingY;
	private final float labelRadius;
	private final float labelGap;
	private final float labelMargin;
	private final float labelBottomMargin;
	private final float labelMaxWidth;
	private final float labelHeight;
	// From the top of the label to the baseline of its text.
	private final float labelBaseline;
	// The names fitted to the label width, by hull key.
	private final Map<String, CharSequence> labelTexts = new HashMap<>();
	private float labelTextsWidth;
	// The labels found this frame, kept from one to the next to spare allocations.
	private final List<Label> labels = new ArrayList<>();
	private int labelCount;
	private ClimbingHulls hulls;
	private double eyeElevation;
	private double eyeHeight;
	private Vector2d viewSize;

	public ClimbingHullView(Context context, AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		areaFillPaint = fillPaint(
				ColorUtils.setAlphaComponent(DisplayableGeoNode.AREA_HULL_COLOR, FILL_ALPHA));
		cragFillPaint = fillPaint(
				ColorUtils.setAlphaComponent(DisplayableGeoNode.CRAG_HULL_COLOR, FILL_ALPHA));
		areaLinePaint = linePaint(ColorUtils.setAlphaComponent(DisplayableGeoNode.AREA_HULL_COLOR,
				LINE_ALPHA), LINE_WIDTH_DP * density);
		cragLinePaint = linePaint(ColorUtils.setAlphaComponent(DisplayableGeoNode.CRAG_HULL_COLOR,
				LINE_ALPHA), LINE_WIDTH_DP * density);
		casingPaint = linePaint(CASING_COLOR, CASING_WIDTH_DP * density);

		labelTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
		labelTextPaint.setColor(Color.BLACK);
		labelTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
				LABEL_TEXT_SP, getResources().getDisplayMetrics()));
		// As on the map, the labels take the opaque colour of the pins.
		areaLabelPaint = fillPaint(ColorUtils.setAlphaComponent(
				DisplayableGeoNode.getHullPinColor(GeoNode.NodeTypes.area), LABEL_ALPHA));
		cragLabelPaint = fillPaint(ColorUtils.setAlphaComponent(
				DisplayableGeoNode.getHullPinColor(GeoNode.NodeTypes.crag), LABEL_ALPHA));
		labelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		labelBorderPaint.setColor(CASING_COLOR);
		labelBorderPaint.setStyle(Paint.Style.STROKE);
		labelBorderPaint.setStrokeWidth(LABEL_BORDER_DP * density);
		labelPaddingX = LABEL_PADDING_X_DP * density;
		labelPaddingY = LABEL_PADDING_Y_DP * density;
		labelRadius = LABEL_RADIUS_DP * density;
		labelGap = LABEL_GAP_DP * density;
		labelMargin = LABEL_MARGIN_DP * density;
		labelBottomMargin = LABEL_BOTTOM_MARGIN_DP * density;
		labelMaxWidth = LABEL_MAX_WIDTH_DP * density;
		Paint.FontMetrics font = labelTextPaint.getFontMetrics();
		labelHeight = font.descent - font.ascent + 2 * labelPaddingY;
		labelBaseline = labelPaddingY - font.ascent;
	}

	private static Paint fillPaint(int color) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(color);
		paint.setStyle(Paint.Style.FILL);
		return paint;
	}

	private static Paint linePaint(int color, float width) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(color);
		paint.setStrokeWidth(width);
		// Round ends join the segments of a line smoothly.
		paint.setStrokeCap(Paint.Cap.ROUND);
		return paint;
	}

	/**
	 * Draws the hulls as {@link Globals#virtualCamera} sees them, from then on.
	 *
	 * @param eyeElevation elevation of the observer's eye above sea level, NaN when unknown
	 * @param eyeHeight    height of the observer's eye above the ground, for where the
	 *                     elevations are unknown
	 * @param viewSize     size in pixel of the camera view, which the angle of view spans
	 */
	public void show(ClimbingHulls hulls, double eyeElevation, double eyeHeight,
	                 Vector2d viewSize) {
		this.hulls = hulls;
		this.eyeElevation = eyeElevation;
		this.eyeHeight = eyeHeight;
		this.viewSize = viewSize;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (hulls == null || hulls.getHulls().isEmpty() || viewSize.x == 0
				|| viewSize.y == 0) {
			return;
		}

		VirtualCamera camera = Globals.virtualCamera;
		projection.setCamera(camera.degAzimuth, -camera.degPitch,
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.x, viewSize.x),
				AugmentedRealityUtils.focalLength(camera.angleOfViewDeg.y, viewSize.y),
				getWidth() / 2.0, getHeight() / 2.0);
		double observerEast = hulls.getEastOfOrigin(camera.decimalLongitude);
		double observerNorth = hulls.getNorthOfOrigin(camera.decimalLatitude);
		View container = (View) getParent();
		float roll = container != null ? container.getRotation() : 0;
		float labelWidthLimit = (float) Math.min(labelMaxWidth, viewSize.x - 2 * labelMargin);

		labelCount = 0;
		for (ClimbingHull hull : hulls.getHulls()) {
			boolean crag = hull.type == GeoNode.NodeTypes.crag;
			ClimbingHull.GroundLine fill = hull.getFill();
			if (fill != null) {
				int corners = projection.projectPolygon(fill, observerEast, observerNorth,
						eyeElevation, eyeHeight);
				if (corners > 2) {
					float[] polygon = projection.getPolygon();
					fillPath.rewind();
					fillPath.moveTo(polygon[0], polygon[1]);
					for (int corner = 1; corner < corners; corner++) {
						fillPath.lineTo(polygon[corner * 2], polygon[corner * 2 + 1]);
					}
					fillPath.close();
					canvas.drawPath(fillPath, crag ? cragFillPaint : areaFillPaint);
				}
			}

			projection.clearLines();
			for (ClimbingHull.GroundLine line : hull.getOutline()) {
				projection.projectLine(line, observerEast, observerNorth, eyeElevation, eyeHeight);
			}
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(), casingPaint);
			canvas.drawLines(projection.getLines(), 0, projection.getLineCount(),
					crag ? cragLinePaint : areaLinePaint);

			if (hull.isShowingRoutes() && !hull.pin.getName().isEmpty()) {
				CharSequence text = labelText(hull, labelWidthLimit - 2 * labelPaddingX);
				float width = labelTextPaint.measureText(text, 0, text.length())
						+ 2 * labelPaddingX;
				if (projection.findLabelAnchor(viewSize.x, viewSize.y, roll, width / 2,
						labelGap + labelHeight, labelMargin, labelBottomMargin)) {
					addLabel(hull, text, width, projection.getLabelX(), projection.getLabelY());
				}
			}
		}

		// Over the outlines, the crags first: they are what the observer is at.
		for (int index = 0; index < labelCount; index++) {
			labels.get(index).drawn = false;
		}
		drawLabels(canvas, GeoNode.NodeTypes.crag);
		drawLabels(canvas, GeoNode.NodeTypes.area);
	}

	private CharSequence labelText(ClimbingHull hull, float maxTextWidth) {
		if (maxTextWidth != labelTextsWidth) {
			labelTexts.clear();
			labelTextsWidth = maxTextWidth;
		}
		CharSequence text = labelTexts.get(hull.key);
		if (text == null) {
			text = TextUtils.ellipsize(hull.pin.getName(), labelTextPaint, maxTextWidth,
					TextUtils.TruncateAt.END);
			labelTexts.put(hull.key, text);
		}
		return text;
	}

	/**
	 * @param x where the label hangs from, in the middle of its top
	 * @param y where the label hangs from, above its top by the gap
	 */
	private void addLabel(ClimbingHull hull, CharSequence text, float width, float x, float y) {
		if (labelCount == labels.size()) {
			labels.add(new Label());
		}
		Label label = labels.get(labelCount++);
		label.hull = hull;
		label.text = text;
		label.box.set(x - width / 2, y + labelGap, x + width / 2, y + labelGap + labelHeight);
	}

	/**
	 * Draws the labels of the type, but those over a label already drawn.
	 */
	private void drawLabels(Canvas canvas, GeoNode.NodeTypes type) {
		for (int index = 0; index < labelCount; index++) {
			Label label = labels.get(index);
			if (label.hull.type != type || overlapsDrawnLabel(label)) {
				continue;
			}
			label.drawn = true;
			canvas.drawRoundRect(label.box, labelRadius, labelRadius,
					type == GeoNode.NodeTypes.crag ? cragLabelPaint : areaLabelPaint);
			canvas.drawRoundRect(label.box, labelRadius, labelRadius, labelBorderPaint);
			canvas.drawText(label.text, 0, label.text.length(), label.box.left + labelPaddingX,
					label.box.top + labelBaseline, labelTextPaint);
		}
	}

	private boolean overlapsDrawnLabel(Label label) {
		for (int index = 0; index < labelCount; index++) {
			Label other = labels.get(index);
			if (other.drawn && RectF.intersects(other.box, label.box)) {
				return true;
			}
		}
		return false;
	}

	private static final class Label {
		private final RectF box = new RectF();
		private ClimbingHull hull;
		private CharSequence text;
		private boolean drawn;
	}
}
