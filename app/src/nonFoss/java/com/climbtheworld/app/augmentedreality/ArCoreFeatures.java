package com.climbtheworld.app.augmentedreality;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.augmentedreality.ARCore.ArPlacementController;
import com.climbtheworld.app.augmentedreality.ARCore.ArViewController;
import com.climbtheworld.app.augmentedreality.placement.ArPlacement;

/**
 * Makes what runs on ARCore in the non-FOSS build: the A.R. placement in the node editor, and the
 * A.R. view's tracking. The FOSS build has its own ArCoreFeatures, without any.
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
}
