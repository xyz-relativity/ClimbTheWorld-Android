package com.climbtheworld.app.utils.constants;

import com.climbtheworld.app.converter.tools.GradeSystem;
import com.climbtheworld.app.utils.Globals;

public interface UIConstants {
	// UI CONFIGS

	//AR icon scaling
	double UI_CLOSEUP_MIN_SCALE_DP = 40;
	double UI_CLOSEUP_MAX_SCALE_DP = 100;
	double UI_FAR_MIN_SCALE_DP = 5;
	double UI_FAR_MAX_SCALE_DP = 40;
	double UI_CLOSE_TO_FAR_THRESHOLD_METERS = 100;

	//POI Icons rendering
	float ICON_MIN_SCALE = 0.3f; //minimum scaling for icon
	float ICON_MAX_SCALE = 3f; //minimum scaling for icon

	//Lists
	double POI_TYPE_LIST_ICON_SIZE = Globals.convertDpToPixel(42);

	//UX CONFIGS
	int ON_TAP_DELAY_MS = 150;
	GradeSystem STANDARD_SYSTEM = GradeSystem.uiaa;
}
