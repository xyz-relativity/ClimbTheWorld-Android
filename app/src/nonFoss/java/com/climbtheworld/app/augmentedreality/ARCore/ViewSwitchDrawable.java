package com.climbtheworld.app.augmentedreality.ARCore;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The icon of the button switching between the map and the A.R.: the unit converter's arrows and
 * circles, ic_view_switch_frame, with an icon in each circle. It turns as the arrows go, the
 * circles trading places, while the icons in them stay upright, see setTurn.
 */
class ViewSwitchDrawable extends Drawable {
	// In the frame's viewport: where the circles are, and how big they are.
	private static final float VIEWPORT = 48f;
	private static final float TOP_CIRCLE_X = 25.287f;
	private static final float TOP_CIRCLE_Y = 10.17f;
	private static final float BOTTOM_CIRCLE_X = 23.057f;
	private static final float BOTTOM_CIRCLE_Y = 37.828f;
	private static final float CIRCLE_RADIUS = 9.171f;
	// Half the width of an icon, as a share of the circle's radius: the square fitting in it.
	private static final float ICON_SHARE = 0.72f;

	private final Drawable frame;
	private final Drawable topIcon;
	private final Drawable bottomIcon;
	private float turnDegrees;

	/**
	 * @param topIcon    in the top circle when not turned
	 * @param bottomIcon in the bottom circle when not turned
	 */
	ViewSwitchDrawable(Drawable frame, Drawable topIcon, Drawable bottomIcon) {
		this.frame = frame.mutate();
		this.topIcon = topIcon.mutate();
		this.bottomIcon = bottomIcon.mutate();
	}

	float getTurn() {
		return turnDegrees;
	}

	/**
	 * Turns the arrows and circles clockwise around the middle between the circles: half a turn
	 * has them trade places.
	 */
	void setTurn(float degrees) {
		turnDegrees = degrees;
		invalidateSelf();
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		float scale = Math.min(bounds.width(), bounds.height()) / VIEWPORT;
		float pivotX = bounds.left + (TOP_CIRCLE_X + BOTTOM_CIRCLE_X) / 2 * scale;
		float pivotY = bounds.top + (TOP_CIRCLE_Y + BOTTOM_CIRCLE_Y) / 2 * scale;

		int saved = canvas.save();
		canvas.rotate(turnDegrees, pivotX, pivotY);
		frame.setBounds(bounds);
		frame.draw(canvas);
		canvas.restoreToCount(saved);

		drawIcon(canvas, topIcon, bounds.left + TOP_CIRCLE_X * scale,
				bounds.top + TOP_CIRCLE_Y * scale, pivotX, pivotY, scale);
		drawIcon(canvas, bottomIcon, bounds.left + BOTTOM_CIRCLE_X * scale,
				bounds.top + BOTTOM_CIRCLE_Y * scale, pivotX, pivotY, scale);
	}

	/**
	 * Draws an icon upright in its circle, wherever the turn has taken the circle.
	 */
	private void drawIcon(Canvas canvas, Drawable icon, float circleX, float circleY,
	                      float pivotX, float pivotY, float scale) {
		double turn = Math.toRadians(turnDegrees);
		double offsetX = circleX - pivotX;
		double offsetY = circleY - pivotY;
		// Clockwise on the screen, whose y goes down, as Canvas.rotate turns.
		double x = pivotX + offsetX * Math.cos(turn) - offsetY * Math.sin(turn);
		double y = pivotY + offsetX * Math.sin(turn) + offsetY * Math.cos(turn);
		double half = CIRCLE_RADIUS * ICON_SHARE * scale;
		icon.setBounds((int) Math.round(x - half), (int) Math.round(y - half),
				(int) Math.round(x + half), (int) Math.round(y + half));
		icon.draw(canvas);
	}

	@Override
	public void setAlpha(int alpha) {
		frame.setAlpha(alpha);
		topIcon.setAlpha(alpha);
		bottomIcon.setAlpha(alpha);
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		frame.setColorFilter(colorFilter);
		topIcon.setColorFilter(colorFilter);
		bottomIcon.setColorFilter(colorFilter);
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public int getIntrinsicWidth() {
		return frame.getIntrinsicWidth();
	}

	@Override
	public int getIntrinsicHeight() {
		return frame.getIntrinsicHeight();
	}
}
