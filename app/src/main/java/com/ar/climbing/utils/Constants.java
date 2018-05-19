package com.ar.climbing.utils;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
    double UI_CLOSEUP_MIN_SCALE = 50;
    double UI_CLOSEUP_MAX_SCALE = 200;
    double UI_FAR_MIN_SCALE = 10;
    double UI_FAR_MAX_SCALE = 50;
    double UI_CLOSE_TO_FAR_THRESHOLD = 100;
    double MAP_ZOOM_LEVEL = 16;
    int ON_TAP_DELAY_MS = 250;
    String STANDARD_SYSTEM = "UIAA";
    long MAP_CENTER_FREES_TIMEOUT_MILLISECONDS = 10000;
    int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;
    String UNKNOWN_GRADE_STRING = "?";

    int POS_UPDATE_ANIMATION_STEPS = 10;

    //OpenStreetMaps
    enum OSM_API {
        OSM_0_6_API (
                "https://api.openstreetmap.org/api/0.6",
                "https://www.openstreetmap.org/"),
        OSM_SANDBOX_0_6_API (
                "https://master.apis.dev.openstreetmap.org/api/0.6",
                "https://master.apis.dev.openstreetmap.org/");

        OSM_API(String apiUrl, String oAuthUrl) {
            this.apiUrl = apiUrl;
            this.oAuthUrl = oAuthUrl;
        }
        public String apiUrl;
        public String oAuthUrl;
    }
    OSM_API DEFAULT_API = OSM_API.OSM_0_6_API;

    //OpenStreetMaps Overpass:
    String OVERPASS_API = "https://overpass-api.de/api/interpreter";

    //Activity events
    int OPEN_EDIT_ACTIVITY = 1001;
    int OPEN_CONFIG_ACTIVITY = 1002;
    int OPEN_OAUTH_ACTIVITY = 1003;
}
