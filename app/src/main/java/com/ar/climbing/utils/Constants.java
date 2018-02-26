package com.ar.climbing.utils;

import okhttp3.OkHttpClient;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
    double UI_MIN_SCALE = 5;
    double UI_MAX_SCALE = 150;
    int MAP_ZOOM_LEVEL = 16;
    int MAP_MAX_ZOOM_LEVEL = 30;
    String STANDARD_SYSTEM = "UIAA";
    long MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    String UNKNOWN_GRADE_STRING = "?";

    OkHttpClient httpClient = new OkHttpClient();

    //Activity events
    int OPEN_EDIT_ACTIVITY = 1234;
}
