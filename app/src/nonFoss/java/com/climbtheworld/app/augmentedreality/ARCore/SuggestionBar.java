package com.climbtheworld.app.augmentedreality.ARCore;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * The bar just above the A.R. align dialog's scroll indicator, see AlignmentPicker: where along
 * the strip of routes the suggested ones are, at the indicator's scale, so the user sees how far
 * the indicator's thumb is from them, and which way to scroll. A tap on it goes to the
 * suggestion whose mark is nearest, see Listener.
 */
class SuggestionBar extends View {
	// The view, over the strip's bottom padding and its scroll indicator, for the bar to be
	// tapped easily: the bar itself is drawn as thin as the indicator, just above it.
	static final int TOUCH_HEIGHT_DP = 14;
	private static final int BAR_HEIGHT_DP = 4;
	private static final int BAR_BOTTOM_DP = 6;
	// So a route's mark shows on a long strip, where its card is narrower than this.
	private static final float MIN_MARK_DP = 6;
	private static final int TRACK_COLOR = Color.argb(0x40, 0x80, 0x80, 0x80);

	interface Listener {
		/**
		 * @param mark the index of the mark tapped, as setMarks has them
		 */
		void onMarkTapped(int mark);
	}

	private final float density;
	private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final Listener listener;
	// Each mark's start and end along the strip, from 0 to 1, in pairs.
	private float[] marks = new float[0];
	// Where the touch on the bar went down, for the tap to go to the mark nearest it.
	private float downX;

	SuggestionBar(Context context, Listener listener) {
		super(context);
		this.listener = listener;
		density = getResources().getDisplayMetrics().density;
		track.setColor(TRACK_COLOR);
		// The colour of the suggested routes' outline, see bg_align_route_suggested.
		TypedValue accent = new TypedValue();
		context.getTheme().resolveAttribute(android.R.attr.colorAccent, accent, true);
		mark.setColor(accent.resourceId != 0 ? ContextCompat.getColor(context, accent.resourceId)
				: accent.data);
		// The outlines on the strip say the same.
		setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
	}

	/**
	 * @param marks each suggested route's start and end along the strip, from 0 to 1, in pairs
	 */
	void setMarks(float[] marks) {
		this.marks = marks;
		invalidate();
	}

	// performClick is called on a tap; the bar is left out of accessibility, see above.
	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (marks.length < 2) {
			return false;
		}
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				downX = event.getX();
				return true;
			case MotionEvent.ACTION_UP:
				listener.onMarkTapped(nearestMark(downX));
				performClick();
				return true;
			default:
				return true;
		}
	}

	private int nearestMark(float x) {
		int nearest = 0;
		float nearestDistance = Float.POSITIVE_INFINITY;
		for (int i = 0; i + 1 < marks.length; i += 2) {
			float distance = Math.abs((marks[i] + marks[i + 1]) / 2 * getWidth() - x);
			if (distance < nearestDistance) {
				nearest = i / 2;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		float bottom = getHeight() - BAR_BOTTOM_DP * density;
		float top = bottom - BAR_HEIGHT_DP * density;
		float radius = (bottom - top) / 2;
		rect.set(0, top, getWidth(), bottom);
		canvas.drawRoundRect(rect, radius, radius, track);
		float minWidth = MIN_MARK_DP * density;
		for (int i = 0; i + 1 < marks.length; i += 2) {
			float start = marks[i] * getWidth();
			float end = marks[i + 1] * getWidth();
			if (end - start < minWidth) {
				float middle = (start + end) / 2;
				start = middle - minWidth / 2;
				end = middle + minWidth / 2;
			}
			rect.set(Math.max(0, start), top, Math.min(getWidth(), end), bottom);
			canvas.drawRoundRect(rect, radius, radius, mark);
		}
	}
}
