package com.climbtheworld.app.utils.views;

import android.graphics.Color;
import android.widget.TextView;

import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.utils.Globals;

/**
 * Styling shared by every label drawn on top of the grade colour gradient. The gradient runs
 * through fully saturated hues, so the label is kept dark and given a white halo to stay legible
 * from the red end to the green one, the same way the markers outline their own labels.
 */
public class GradeViewUtils {
	private static final float HALO_RADIUS_PX =
			Globals.convertDpToPixel(DisplayableGeoNode.MARKER_TEXT_OUTLINE_DP).floatValue();

	private GradeViewUtils() {
		//hide constructor
	}

	/**
	 * Paints the grade colour behind the label and makes the label readable over it.
	 */
	public static void styleGradeLabel(TextView label, int backgroundColor) {
		label.setBackgroundColor(backgroundColor);
		styleGradeText(label, Color.BLACK);
	}

	/**
	 * Paints the grade colour behind the label with a dimmed text colour, for entries that are
	 * shown but cannot be selected.
	 */
	public static void styleGradeLabel(TextView label, int backgroundColor, int textColor) {
		label.setBackgroundColor(backgroundColor);
		styleGradeText(label, textColor);
	}

	/**
	 * Applies the label styling alone, for views that receive the grade colour from their
	 * container rather than from their own background.
	 */
	public static void styleGradeText(TextView label, int textColor) {
		label.setTextColor(textColor);
		label.setShadowLayer(HALO_RADIUS_PX, 0, 0, Color.WHITE);
	}
}
