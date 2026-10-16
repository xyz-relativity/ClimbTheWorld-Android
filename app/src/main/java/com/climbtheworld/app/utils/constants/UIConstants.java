package com.climbtheworld.app.utils.constants;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;

public interface UIConstants {
	// UI CONFIGS

	//AR pin scaling: the height of a crag pin up to the near distance, and at the farthest view
	//distance
	double UI_NEAR_PIN_HEIGHT_DP = 100;
	double UI_NEAR_PIN_DISTANCE_METERS = 10;
	double UI_FAR_PIN_HEIGHT_DP = 20;

	//Lists
	double POI_TYPE_LIST_ICON_SIZE = Globals.convertDpToPixel(42);

	//UX CONFIGS
	int ON_TAP_DELAY_MS = 150;
	GradeSystem STANDARD_SYSTEM = GradeSystem.uiaa;
}
