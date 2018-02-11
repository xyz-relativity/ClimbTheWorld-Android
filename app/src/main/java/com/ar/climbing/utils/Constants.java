package com.ar.climbing.utils;

import okhttp3.OkHttpClient;

/**
 * Created by xyz on 12/27/17.
 */

public class Constants {
    public static final double UI_MIN_SCALE = 5;
    public static final double UI_MAX_SCALE = 150;
    public static final int MAP_ZOOM_LEVEL = 16;
    public static final int MAP_MAX_ZOOM_LEVEL = 30;
    public static final String STANDARD_SYSTEM = "UIAA";
    public static final long MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    public static final int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    public static final String UNKNOWN_GRADE_STRING = "?";

    public static final OkHttpClient httpClient = new OkHttpClient();

    //Activity events
    public static final int OPEN_EDIT_ACTIVITY = 1234;

    private Constants() {
        //hide constructor
    }
}
