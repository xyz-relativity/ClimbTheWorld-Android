package com.climbtheworld.app.augmentedreality.placement;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.map.widget.MapLibreMapWidget;

/**
 * Makes the A.R. placement of the FOSS build, which has none, as it leaves ARCore out: its button
 * never shows. The non-FOSS build has its own ArPlacements.
 */
public final class ArPlacements {
	private ArPlacements() {
	}

	public static ArPlacement create(AppCompatActivity activity, View container,
	                                 MapLibreMapWidget editorMap,
	                                 ArPlacement.PlaceListener placeListener) {
		return new ArPlacement() {
		};
	}
}
