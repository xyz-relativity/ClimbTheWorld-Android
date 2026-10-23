package com.climbtheworld.app.navigate.widgets;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
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
 * explains it again, or for a compass that needs calibrating, shows how until it is calibrated.
 */
public class CompassStatusBadge {
	// Shared by every badge, so that a problem already explained on one compass, on this screen or
	// another, is not explained again.
	private static final Set<OrientationManager.CompassStatus> explainedStatuses =
			EnumSet.noneOf(OrientationManager.CompassStatus.class);

	private final ImageView badge;
	private OrientationManager.CompassStatus status = OrientationManager.CompassStatus.OK;
	private AlertDialog calibrationDialog;

	/**
	 * @param badge the view showing the badge, hidden until the compass cannot be trusted
	 */
	public CompassStatusBadge(ImageView badge) {
		this.badge = badge;
		badge.setOnClickListener(view -> {
			if (status == OrientationManager.CompassStatus.NEEDS_CALIBRATION) {
				showCalibration();
			} else {
				explain();
			}
		});
		// The dialog goes with the screen it shows over.
		badge.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
			@Override
			public void onViewAttachedToWindow(View view) {
			}

			@Override
			public void onViewDetachedFromWindow(View view) {
				if (calibrationDialog != null) {
					calibrationDialog.dismiss();
				}
			}
		});
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
		if (calibrationDialog != null
				&& status != OrientationManager.CompassStatus.NEEDS_CALIBRATION) {
			// Calibrated, which the user was waiting for, so what comes next is said even when
			// it was explained before.
			calibrationDialog.dismiss();
			if (status == OrientationManager.CompassStatus.OK) {
				show(R.string.compass_calibrated);
			} else {
				explainedStatuses.add(status);
				explain();
			}
		} else if (status != OrientationManager.CompassStatus.OK && explainedStatuses.add(status)) {
			explain();
		}
	}

	/**
	 * The badge, an infinity sign for calibrating or a magnet for a bent magnetic field, or 0 when
	 * the compass is fine.
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
		show(explanation(status));
	}

	private void show(int message) {
		Snackbar snackbar = Snackbar.make(badge, message, Snackbar.LENGTH_LONG);
		snackbar.setTextMaxLines(5);
		snackbar.show();
	}

	/**
	 * Shows how to calibrate the compass, until it is calibrated, see setStatus(). The screen
	 * keeps its orientation meanwhile: turning the phone every way would otherwise turn the
	 * screen, which recreates most screens, and the dialog with them.
	 */
	private void showCalibration() {
		Context context = badge.getContext();
		AlertDialog dialog = new AlertDialog.Builder(context)
				.setIcon(R.drawable.ic_compass_badge_calibrate)
				.setTitle(R.string.compass_calibration_title)
				.setMessage(R.string.compass_calibration_message)
				.setNegativeButton(R.string.cancel, null)
				.create();
		dialog.setView(new CompassCalibrationView(context));
		// The phone is waved around meanwhile, so a touch outside the dialog is likely a slip.
		dialog.setCanceledOnTouchOutside(false);

		Activity activity = activityOf(context);
		if (activity != null) {
			int orientation = activity.getRequestedOrientation();
			activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
			dialog.setOnDismissListener(dismissed -> {
				calibrationDialog = null;
				activity.setRequestedOrientation(orientation);
			});
		} else {
			dialog.setOnDismissListener(dismissed -> calibrationDialog = null);
		}
		calibrationDialog = dialog;
		dialog.show();
	}

	private static Activity activityOf(Context context) {
		while (context instanceof ContextWrapper) {
			if (context instanceof Activity) {
				return (Activity) context;
			}
			context = ((ContextWrapper) context).getBaseContext();
		}
		return null;
	}
}
