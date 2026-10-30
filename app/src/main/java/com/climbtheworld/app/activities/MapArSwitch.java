package com.climbtheworld.app.activities;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.views.ViewSwitchDrawable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * The button switching between the map and the A.R. view, at the bottom right of both, as the node
 * editor's between its map and the A.R. placement, with the same icon: the view to switch to in its
 * top circle. A view the switch opened switches back by closing, back to the other one as it was.
 */
final class MapArSwitch {
	// On the intent of the view the switch opened.
	private static final String EXTRA_SWITCHED = "mapArSwitched";

	private MapArSwitch() {
	}

	/**
	 * @param inAr whether the button is in the A.R. view, rather than on the map
	 */
	static void setUp(AppCompatActivity activity, FloatingActionButton button, boolean inAr) {
		ViewSwitchDrawable icon = new ViewSwitchDrawable(
				AppCompatResources.getDrawable(activity, R.drawable.ic_view_switch_frame),
				AppCompatResources.getDrawable(activity, R.drawable.ic_view_topo_ar),
				AppCompatResources.getDrawable(activity, R.drawable.ic_world_map));
		float rest = inAr ? 180 : 0;
		icon.setTurn(rest);
		button.setImageDrawable(icon);
		button.setContentDescription(activity.getString(
				inAr ? R.string.hint_switch_to_map : R.string.hint_switch_to_ar));
		button.setOnClickListener(view -> {
			if (icon.isTurning() || (!inAr && !AugmentedRealityActivity.checkCompass(activity))) {
				return;
			}
			// The icon turns as the editor's, then the view switches.
			icon.turnTo(!inAr, () -> switchView(activity, inAr));
		});
		// Turned back once out of sight, for when the user comes back to this view.
		activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
			@Override
			public void onStop(@NonNull LifecycleOwner owner) {
				icon.setTurn(rest);
			}
		});
	}

	private static void switchView(AppCompatActivity activity, boolean inAr) {
		if (activity.getIntent().getBooleanExtra(EXTRA_SWITCHED, false)) {
			activity.finish();
			return;
		}
		Intent intent = new Intent(activity,
				inAr ? MapActivity.class : AugmentedRealityActivity.class);
		intent.putExtra(EXTRA_SWITCHED, true);
		activity.startActivity(intent);
	}
}
