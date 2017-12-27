package com.ar.openClimbAR.utils;

import okhttp3.OkHttpClient;

/**
 * Created by xyz on 12/27/17.
 */

public class Constants {
    public static final float MAX_DISTANCE_METERS = 50f;
    public static final float MIN_DISTANCE_METERS = 0f;
    public static final float UI_MIN_SCALE = 20f;
    public static final float UI_MAX_SCALE = 300f;
    public static final int MAX_SHOW_NODES = 100;
    public static final int MAP_ZOOM_LEVEL = 16;

    public static final String UNKNOWN_GRADE_STRING = "?";

    public static final OkHttpClient httpClient = new OkHttpClient();

    private Constants() {
        //hide constructor
    }
}
