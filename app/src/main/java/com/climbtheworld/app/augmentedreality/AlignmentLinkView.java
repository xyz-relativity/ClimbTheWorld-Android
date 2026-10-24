package com.climbtheworld.app.augmentedreality;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import com.climbtheworld.app.map.DisplayableGeoNode;

import java.util.function.Supplier;

/**
 * Draws a dashed line from the tip of one pin to the tip of another, wherever each is in the
 * activity's views: from the pin of the route being aligned on where the A.R. has it now, drawn
 * see-through by the scene, see AugmentedRealityScene.showOnly, to its pin riding on the cursor,
 * which shows how far the A.R. is off until the route is dropped; with the route out of view,
 * the scene lays its pin out off the view in its direction, so the line shows the way to turn to
 * it. The line is in the route's grade colour along a dark edge, as the route lines are. It fills
 * the view it sits in, which is not turned, and draws again when asked, see update, as the pins
 * move.
 */
public class AlignmentLinkView extends View {
	// The dashes and the gaps between them, in dp.
	private static final float DASH_DP = 8;
	private static final float GAP_DP = 6;

	private final Paint linePaint;
	private final Paint casingPaint;
	private final Path path = new Path();
	private final float[] from = new float[2];
	private final float[] to = new float[2];
	private final float[] origin = new float[2];
	// The pin the line starts at, null while it is not on display, and the one it ends at.
	private Supplier<View> fromPin;
	private View toPin;

	public AlignmentLinkView(Context context, AttributeSet attrs) {
		super(context, attrs);
		float density = getResources().getDisplayMetrics().density;
		DashPathEffect dashes = new DashPathEffect(
				new float[]{DASH_DP * density, GAP_DP * density}, 0);
		linePaint = ClimbingHullView.linePaint(0, DisplayableGeoNode.LINE_WIDTH_DP * density);
		casingPaint = ClimbingHullView.linePaint(DisplayableGeoNode.LINE_CASING_COLOR,
				DisplayableGeoNode.LINE_CASING_WIDTH_DP * density);
		// Drawn as a path, for the dashes, which fills unless told to stroke: the other views
		// draw lines, which stroke whatever the style.
		for (Paint paint : new Paint[]{linePaint, casingPaint}) {
			paint.setStyle(Paint.Style.STROKE);
			paint.setPathEffect(dashes);
		}
	}

	/**
	 * Where the tip of a pin is in the window: the bottom middle of its icon, which its scaling
	 * and turning keep in place, see AugmentedRealityViewManager.placePin.
	 */
	private static void tipInWindow(View pin, float[] point) {
		Drawable icon = ((ImageView) pin).getDrawable();
		point[0] = icon.getIntrinsicWidth() / 2f;
		point[1] = icon.getIntrinsicHeight();
		toWindow(pin, point);
	}

	/**
	 * Moves a point of a view's own coordinates into the window's, through the views it sits in,
	 * as each is moved, scaled and turned, which View.getLocationInWindow only does for the
	 * view's corner.
	 */
	private static void toWindow(View view, float[] point) {
		View current = view;
		while (true) {
			current.getMatrix().mapPoints(point);
			point[0] += current.getLeft();
			point[1] += current.getTop();
			ViewParent parent = current.getParent();
			if (!(parent instanceof View)) {
				return;
			}
			current = (View) parent;
			point[0] -= current.getScrollX();
			point[1] -= current.getScrollY();
		}
	}

	/**
	 * Draws the line from then on, whenever the view is drawn, see update.
	 *
	 * @param fromPin the pin the line starts at, null while it is not on display; the line
	 *                shows only while both pins do
	 * @param toPin   the pin it ends at
	 * @param color   the route's grade colour
	 */
	public void show(Supplier<View> fromPin, View toPin, int color) {
		this.fromPin = fromPin;
		this.toPin = toPin;
		linePaint.setColor(ColorUtils.setAlphaComponent(color, DisplayableGeoNode.LINE_ALPHA));
		setVisibility(VISIBLE);
		invalidate();
	}

	public void hide() {
		fromPin = null;
		toPin = null;
		setVisibility(GONE);
	}

	/**
	 * Draws the line again, where the pins are now.
	 */
	public void update() {
		if (getVisibility() == VISIBLE) {
			invalidate();
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		View start = fromPin == null ? null : fromPin.get();
		if (start == null || toPin == null || !start.isShown() || !toPin.isShown()) {
			return;
		}
		tipInWindow(start, from);
		tipInWindow(toPin, to);
		origin[0] = 0;
		origin[1] = 0;
		toWindow(this, origin);
		path.reset();
		path.moveTo(from[0] - origin[0], from[1] - origin[1]);
		path.lineTo(to[0] - origin[0], to[1] - origin[1]);
		canvas.drawPath(path, casingPaint);
		canvas.drawPath(path, linePaint);
	}
}
