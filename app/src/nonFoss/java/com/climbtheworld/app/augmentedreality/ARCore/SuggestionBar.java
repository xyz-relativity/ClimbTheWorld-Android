package com.climbtheworld.app.augmentedreality.ARCore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * The bar just above the A.R. align dialog's scroll indicator, see AlignmentPicker: where along
 * the strip of routes the suggested ones are, at the indicator's scale, so the user sees how far
 * the indicator's thumb is from them, and which way to scroll.
 */
class SuggestionBar extends View {
	// As thin as the scroll indicator.
	static final int HEIGHT_DP = 4;
	// So a route's mark shows on a long strip, where its card is narrower than this.
	private static final float MIN_MARK_DP = 6;
	private static final int TRACK_COLOR = Color.argb(0x40, 0x80, 0x80, 0x80);

	private final float density;
	private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	// Each mark's start and end along the strip, from 0 to 1, in pairs.
	private float[] marks = new float[0];

	SuggestionBar(Context context) {
		super(context);
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

	@Override
	protected void onDraw(@NonNull Canvas canvas) {
		float radius = getHeight() / 2f;
		rect.set(0, 0, getWidth(), getHeight());
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
			rect.set(Math.max(0, start), 0, Math.min(getWidth(), end), getHeight());
			canvas.drawRoundRect(rect, radius, radius, mark);
		}
	}
}
