package com.climbtheworld.app.map.widget;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An arc around the rim of the compass button, either side of where its needle points, as wide
 * as the heading may be off. Meant as the button's foreground, so that it turns with the button.
 */
class CompassAccuracyArcDrawable extends Drawable {
	// As fractions of the button's radius: in the ring between the compass icon and the edge.
	private static final float ARC_RADIUS = 0.71f;
	private static final float ARC_WIDTH = 0.14f;
	// The dark edge either side of the arc, so it shows on any button colour, like the black
	// outline of the compass icon.
	private static final float CASING_WIDTH = 0.04f;
	private static final int ARC_COLOR = 0xffffffff;
	private static final int CASING_COLOR = 0xcc000000;

	private final Paint arcPaint = createPaint(ARC_COLOR);
	private final Paint casingPaint = createPaint(CASING_COLOR);
	private final RectF oval = new RectF();
	private int halfWidthDegrees;

	private static Paint createPaint(int color) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setColor(color);
		return paint;
	}

	/**
	 * @param halfWidthDegrees how far either side of the needle the arc reaches, 0 for no arc
	 *                         and 180 or more for a full circle
	 */
	void setHalfWidthDegrees(int halfWidthDegrees) {
		if (this.halfWidthDegrees != halfWidthDegrees) {
			this.halfWidthDegrees = halfWidthDegrees;
			invalidateSelf();
		}
	}

	int getHalfWidthDegrees() {
		return halfWidthDegrees;
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		if (halfWidthDegrees <= 0) {
			return;
		}
		Rect bounds = getBounds();
		float radius = Math.min(bounds.width(), bounds.height()) / 2f;
		float arcRadius = radius * ARC_RADIUS;
		oval.set(bounds.exactCenterX() - arcRadius, bounds.exactCenterY() - arcRadius,
				bounds.exactCenterX() + arcRadius, bounds.exactCenterY() + arcRadius);
		arcPaint.setStrokeWidth(radius * ARC_WIDTH);
		casingPaint.setStrokeWidth(radius * (ARC_WIDTH + 2 * CASING_WIDTH));
		// Arcs are measured clockwise from the right, so up is at -90 degrees.
		int sweep = Math.min(360, 2 * halfWidthDegrees);
		float start = -90 - sweep / 2f;
		canvas.drawArc(oval, start, sweep, false, casingPaint);
		canvas.drawArc(oval, start, sweep, false, arcPaint);
	}

	@Override
	public void setAlpha(int alpha) {
		arcPaint.setAlpha(alpha);
		casingPaint.setAlpha(Color.alpha(CASING_COLOR) * alpha / 255);
		invalidateSelf();
	}

	@Override
	public void setColorFilter(@Nullable ColorFilter colorFilter) {
		arcPaint.setColorFilter(colorFilter);
		casingPaint.setColorFilter(colorFilter);
		invalidateSelf();
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}
}
