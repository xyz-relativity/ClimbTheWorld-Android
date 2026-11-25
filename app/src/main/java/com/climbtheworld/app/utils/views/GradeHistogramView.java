package com.climbtheworld.app.utils.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.utils.Globals;

import java.util.Collections;
import java.util.Map;

/**
 * Draws a {@link GradeHistogram}, each bar in its grade's colour, the routes the display filters
 * leave out faded as their pins.
 */
public class GradeHistogramView extends View {
	private static final float MIN_BAR_WIDTH = 1;
	private static final float MIN_BAR_HEIGHT = Globals.convertDpToPixel(1).floatValue();

	private final Paint paint = new Paint();
	private Map<Integer, Integer> gradeCounts = Collections.emptyMap();
	private Map<Integer, Integer> matchingGradeCounts = Collections.emptyMap();
	private int scale = 1;

	public GradeHistogramView(Context context) {
		super(context);
	}

	public GradeHistogramView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * @param matchingGradeCounts see {@link GradeHistogram#bars}
	 * @param scale               route count of a full height bar
	 */
	public void setGrades(Map<Integer, Integer> gradeCounts,
	                      Map<Integer, Integer> matchingGradeCounts, int scale) {
		this.gradeCounts = gradeCounts;
		this.matchingGradeCounts = matchingGradeCounts;
		this.scale = scale;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float width = getWidth() - getPaddingLeft() - getPaddingRight();
		float height = getHeight() - getPaddingTop() - getPaddingBottom();
		if (width <= 0 || height <= 0) {
			return;
		}
		canvas.save();
		canvas.translate(getPaddingLeft(), getPaddingTop());
		for (GradeHistogram.Bar bar : GradeHistogram.bars(gradeCounts, matchingGradeCounts,
				GradeSystem.maxGrades, scale, width, height, MIN_BAR_WIDTH, MIN_BAR_HEIGHT)) {
			paint.setColor(Globals.gradeToColorState(bar.grade()).getDefaultColor());
			if (bar.matchingTop() < height) {
				canvas.drawRect(bar.left(), bar.matchingTop(), bar.right(), height, paint);
			}
			if (bar.top() < bar.matchingTop()) {
				paint.setAlpha(DisplayableGeoNode.POI_ICON_ALPHA_HIDDEN);
				canvas.drawRect(bar.left(), bar.top(), bar.right(), bar.matchingTop(), paint);
			}
		}
		canvas.restore();
	}
}
