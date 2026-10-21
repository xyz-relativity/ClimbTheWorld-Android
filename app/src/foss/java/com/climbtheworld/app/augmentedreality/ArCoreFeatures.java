package com.climbtheworld.app.augmentedreality;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.augmentedreality.placement.ArPlacement;
import com.climbtheworld.app.map.widget.MapLibreMapWidget;

/**
 * Makes what runs on ARCore, which the FOSS build leaves out, as ARCore is not free software: the
 * node editor has no A.R. placement, its button never showing, and the A.R. view runs on the
 * sensors. The non-FOSS build has its own ArCoreFeatures.
 */
public final class ArCoreFeatures {
	// Whether this build has what runs on ARCore: it does not.
	public static final boolean AVAILABLE = false;

	private ArCoreFeatures() {
	}

	public static ArPlacement placement(AppCompatActivity activity, View container,
	                                    MapLibreMapWidget editorMap,
	                                    ArPlacement.PlaceListener placeListener) {
		return new ArPlacement() {
		};
	}

	public static ArViewTracking viewTracking(AppCompatActivity activity, View container,
	                                          ArViewTracking.Listener listener) {
		return new ArViewTracking() {
		};
	}
}
