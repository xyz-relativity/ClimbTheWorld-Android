package com.climbtheworld.app.augmentedreality;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.augmentedreality.ARCore.ArPlacementController;
import com.climbtheworld.app.augmentedreality.ARCore.ArViewController;
import com.climbtheworld.app.augmentedreality.placement.ArPlacement;
import com.climbtheworld.app.tutorial.ArCoreFragment;
import com.climbtheworld.app.tutorial.TutorialFragment;
import com.google.ar.core.ArCoreApk;

import java.util.function.Consumer;

/**
 * Makes what runs on ARCore in the non-FOSS build: the A.R. placement in the node editor, the
 * A.R. view's tracking, and the setup wizard's page offering to install ARCore. The FOSS build has
 * its own ArCoreFeatures, without any.
 */
public final class ArCoreFeatures {
	// Whether this build has what runs on ARCore: it does.
	public static final boolean AVAILABLE = true;

	private ArCoreFeatures() {
	}

	/**
	 * See ArPlacementController.
	 */
	public static ArPlacement placement(AppCompatActivity activity, View container,
	                                    ArPlacement.PlaceListener placeListener) {
		return new ArPlacementController(activity, container, placeListener);
	}

	/**
	 * See ArViewController.
	 */
	public static ArViewTracking viewTracking(AppCompatActivity activity, View container,
	                                          ArViewTracking.Listener listener) {
		return new ArViewController(activity, container, listener);
	}

	/**
	 * Gives the page, see ArCoreFragment, on the main thread, only where ARCore is known to run,
	 * as the A.R. view offers to install it: while it is missing, that takes asking Google.
	 */
	public static void tutorialPage(AppCompatActivity activity, Consumer<TutorialFragment> page) {
		ArCoreApk.getInstance().checkAvailabilityAsync(activity.getApplicationContext(),
				availability -> {
					if (availability.isSupported()) {
						page.accept(new ArCoreFragment(activity,
								R.layout.fragment_tutorial_arcore));
					}
				});
	}
}
