package com.climbtheworld.app.navigate.widgets;

import android.view.View;
import android.widget.ImageView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.google.android.material.snackbar.Snackbar;

import java.util.EnumSet;
import java.util.Set;

/**
 * A badge next to a compass while the compass cannot be trusted, showing what is wrong. Each
 * problem is explained the first time it comes up in a run of the app, and tapping the badge
 * explains it again.
 */
public class CompassStatusBadge {
	// Shared by every badge, so that a problem already explained on one compass, on this screen or
	// another, is not explained again.
	private static final Set<OrientationManager.CompassStatus> explainedStatuses =
			EnumSet.noneOf(OrientationManager.CompassStatus.class);

	private final ImageView badge;
	private OrientationManager.CompassStatus status = OrientationManager.CompassStatus.OK;

	/**
	 * @param badge the view showing the badge, hidden until the compass cannot be trusted
	 */
	public CompassStatusBadge(ImageView badge) {
		this.badge = badge;
		badge.setOnClickListener(view -> explain());
	}

	public void setStatus(OrientationManager.CompassStatus status) {
		if (status == this.status) {
			return;
		}
		this.status = status;
		int icon = badgeIcon(status);
		if (icon == 0) {
			badge.setVisibility(View.GONE);
		} else {
			badge.setImageResource(icon);
			badge.setContentDescription(badge.getContext().getString(explanation(status)));
			badge.setVisibility(View.VISIBLE);
		}
		if (status != OrientationManager.CompassStatus.OK && explainedStatuses.add(status)) {
			explain();
		}
	}

	/**
	 * The badge, a figure 8 for calibrating or a magnet for a bent magnetic field, or 0 when the
	 * compass is fine.
	 */
	static int badgeIcon(OrientationManager.CompassStatus status) {
		switch (status) {
			case NEEDS_CALIBRATION:
				return R.drawable.ic_compass_badge_calibrate;
			case DISTURBED:
				return R.drawable.ic_compass_badge_disturbed;
			default:
				return 0;
		}
	}

	private static int explanation(OrientationManager.CompassStatus status) {
		return status == OrientationManager.CompassStatus.DISTURBED
				? R.string.compass_disturbed : R.string.compass_needs_calibration;
	}

	private void explain() {
		if (status == OrientationManager.CompassStatus.OK) {
			return;
		}
		Snackbar snackbar = Snackbar.make(badge, explanation(status), Snackbar.LENGTH_LONG);
		snackbar.setTextMaxLines(5);
		snackbar.show();
	}
}
