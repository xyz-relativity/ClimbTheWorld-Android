package com.ar.climbing.utils;

import okhttp3.OkHttpClient;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
    double UI_MIN_SCALE = 5;
    double UI_MAX_SCALE = 300;
    int MAP_ZOOM_LEVEL = 16;
    int MAP_MAX_ZOOM_LEVEL = 30;
    int ON_TAP_DELAY_MS = 250;
    String STANDARD_SYSTEM = "UIAA";
    long MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    String UNKNOWN_GRADE_STRING = "?";

    OkHttpClient httpClient = new OkHttpClient();

    //OpenStreetMaps
    String DEFAULT_API               = "https://api.openstreetmap.org/api/0.6/";
    String DEFAULT_API_NAME          = "OpenStreetMap";
    String DEFAULT_API_NO_HTTPS      = "http://api.openstreetmap.org/api/0.6/";
    String DEFAULT_API_NO_HTTPS_NAME = "OpenStreetMap no https";
    String SANDBOX_API               = "https://master.apis.dev.openstreetmap.org/api/0.6/";
    String SANDBOX_API_NAME          = "OpenStreetMap sandbox";

    //Activity events
    int OPEN_EDIT_ACTIVITY = 1234;
}
