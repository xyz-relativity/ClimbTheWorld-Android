package com.ar.openClimbAR.utils;

import okhttp3.OkHttpClient;

/**
 * Created by xyz on 12/27/17.
 */

public class Constants {
    public static final float MAX_DISTANCE_METERS = 50f;
    public static final float MIN_DISTANCE_METERS = 0f;
    public static final float UI_MIN_SCALE = 5f;
    public static final float UI_MAX_SCALE = 200f;
    public static final int MAX_SHOW_NODES = 20;
    public static final int MAP_ZOOM_LEVEL = 16;
    public static final int MAP_MAX_ZOOM_LEVEL = 30;
    public static final String DEFAULT_SYSTEM = "UIAA";
    public static final String DISPLAY_SYSTEM = "YDS";

    public static final float MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    public static final int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    public static final long MAP_REFRESH_INTERVAL_MS = 100;

    public static final String UNKNOWN_GRADE_STRING = "?";

    public static final boolean KEEP_SCREEN_ON = true;

    public static final OkHttpClient httpClient = new OkHttpClient();

    public static String[] CARDINAL_NAMES;

    private Constants() {
        //hide constructor
    }
}
