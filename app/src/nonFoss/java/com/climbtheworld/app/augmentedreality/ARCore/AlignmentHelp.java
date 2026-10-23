package com.climbtheworld.app.augmentedreality.ARCore;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.AugmentedRealityViewManager;
import com.climbtheworld.app.map.DisplayableGeoNode;
import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;
import com.climbtheworld.app.utils.constants.UIConstants;

import java.util.Locale;

/**
 * How to align the A.R. on mapped routes, see ArCoreSession.align, in a dialog with a diagram of
 * each step, opened from the info icon on Align.
 */
final class AlignmentHelp {
	// The grades of the routes in the diagrams, in the standard system, for pins of different
	// colours.
	private static final String[] ROUTE_GRADES = {"6", "4", "8"};
	private static final int[] STEP_TEXTS = {R.string.ar_align_help_pick,
			R.string.ar_align_help_drop, R.string.ar_align_help_second_route};
	// In dp: the dialog's text is as far from its sides as an alert's message.
	private static final int SIDE_PADDING = 24;
	private static final int TOP_PADDING = 8;
	private static final int STEP_SPACING = 12;

	private AlignmentHelp() {
		//hide constructor
	}

	static void show(AppCompatActivity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		Context context = builder.getContext();
		float density = context.getResources().getDisplayMetrics().density;
		Drawable[] pins = new Drawable[ROUTE_GRADES.length];
		for (int route = 0; route < pins.length; route++) {
			pins[route] = AugmentedRealityViewManager.createIcon(activity,
					sampleRoute(ROUTE_GRADES[route]));
		}

		LinearLayout content = new LinearLayout(context);
		content.setOrientation(LinearLayout.VERTICAL);
		int side = Math.round(SIDE_PADDING * density);
		content.setPadding(side, Math.round(TOP_PADDING * density), side, 0);
		content.addView(text(context, R.string.ar_align_help_message));
		AlignmentDiagramView.Step[] steps = AlignmentDiagramView.Step.values();
		for (int step = 0; step < steps.length; step++) {
			LinearLayout row = new LinearLayout(context);
			row.setGravity(Gravity.CENTER_VERTICAL);
			row.addView(new AlignmentDiagramView(context, steps[step], pins));
			LinearLayout.LayoutParams textParams =
					new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
			textParams.setMarginStart(Math.round(STEP_SPACING * density));
			row.addView(text(context, STEP_TEXTS[step]), textParams);
			LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			rowParams.topMargin = Math.round(STEP_SPACING * density);
			content.addView(row, rowParams);
		}
		ScrollView scroll = new ScrollView(context);
		scroll.addView(content);

		builder.setTitle(R.string.ar_align_help_title)
				.setView(scroll)
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private static TextView text(Context context, int text) {
		TextView view = new TextView(context);
		view.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
		view.setText(text);
		return view;
	}

	/**
	 * A route with no name, so that its pin shows its grade's colour and nothing else.
	 */
	private static DisplayableGeoNode sampleRoute(String grade) {
		GeoNode route = new GeoNode(0, 0, 0);
		route.setClimbingType(OsmEntity.EntityClimbingType.route);
		route.setKey(String.format(Locale.ROOT, ClimbingTags.KEY_GRADE_TAG,
				UIConstants.STANDARD_SYSTEM.getMainKey()), grade);
		return new DisplayableGeoNode(route);
	}
}
