package com.ar.climbing.utils;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
    double UI_MIN_SCALE = 5;
    double UI_MAX_SCALE = 200;
    double MAP_ZOOM_LEVEL = 16;
    double MAP_MAX_ZOOM_LEVEL = 30;
    int ON_TAP_DELAY_MS = 250;
    String STANDARD_SYSTEM = "UIAA";
    long MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    String UNKNOWN_GRADE_STRING = "?";

    //OpenStreetMaps
    String OSM_0_6_API = "https://api.openstreetmap.org/api/0.6/";
    String OSM_SANDBOX_0_6_API = "https://master.apis.dev.openstreetmap.org/api/0.6/";
    String DEFAULT_API = OSM_SANDBOX_0_6_API;

    //OpenStreetMaps Overpass:
    String OVERPASS_API = "https://overpass-api.de/api/interpreter";

    //Activity events
    int OPEN_EDIT_ACTIVITY = 1001;
    int OPEN_CONFIG_ACTIVITY = 1002;
    int OPEN_OAUTH_ACTIVITY = 1003;
}
