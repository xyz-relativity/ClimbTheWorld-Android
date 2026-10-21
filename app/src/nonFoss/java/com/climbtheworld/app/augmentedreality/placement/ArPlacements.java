package com.climbtheworld.app.augmentedreality.placement;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.augmentedreality.ARCore.ArPlacementController;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;

/**
 * Makes the A.R. placement of the non-FOSS build, on ARCore. The FOSS build has its own
 * ArPlacements, without any.
 */
public final class ArPlacements {
	private ArPlacements() {
	}

	/**
	 * See ArPlacementController.
	 */
	public static ArPlacement create(AppCompatActivity activity, View container,
	                                 MapLibreMapWidget editorMap,
	                                 ArPlacement.PlaceListener placeListener) {
		return new ArPlacementController(activity, container, editorMap, placeListener);
	}
}
