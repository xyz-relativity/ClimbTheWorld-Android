package com.climbtheworld.app.utils;


import com.climbtheworld.app.converter.tools.GradeSystem;

import java.util.UUID;

import needle.BackgroundThreadExecutor;
import needle.Needle;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
    UUID myUUID = UUID.randomUUID();
    long HTTP_TIMEOUT_SECONDS = 240;
    double UI_CLOSEUP_MIN_SCALE_DP = 40;
    double UI_CLOSEUP_MAX_SCALE_DP = 150;
    double UI_FAR_MIN_SCALE_DP = 5;
    double UI_FAR_MAX_SCALE_DP = 40;
    double UI_CLOSE_TO_FAR_THRESHOLD_METERS = 100;
    int ON_TAP_DELAY_MS = 150;
    GradeSystem STANDARD_SYSTEM = GradeSystem.uiaa;
    int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;

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
    String[] OVERPASS_API = {
            "https://overpass-api.de/api/interpreter",
//            "https://overpass.kumi.systems/api/interpreter"
    };

    //Activity events
    int OPEN_EDIT_ACTIVITY = 1001;
    int OPEN_CONFIG_ACTIVITY = 1002;
    int OPEN_OAUTH_ACTIVITY = 1003;

    //Needle task pools
    String NEEDLE_MAP_TASK = "mapTask";
    int NEEDLE_MAP_POOL = 1;
    BackgroundThreadExecutor MAP_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_MAP_TASK)
            .withThreadPoolSize(Constants.NEEDLE_MAP_POOL);

    String NEEDLE_AR_TASK = "arTask";
    int NEEDLE_AR_POOL = 1;
    BackgroundThreadExecutor AR_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_AR_TASK)
            .withThreadPoolSize(Constants.NEEDLE_AR_POOL);

    String NEEDLE_DB_TASK = "dbTask";
    int NEEDLE_DB_POOL = 1;
    BackgroundThreadExecutor DB_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_DB_TASK)
            .withThreadPoolSize(Constants.NEEDLE_DB_POOL);

    String NEEDLE_WEB_TASK = "webTask";
    int NEEDLE_WEB_POOL = 2;
    BackgroundThreadExecutor WEB_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_WEB_TASK)
            .withThreadPoolSize(Constants.NEEDLE_WEB_POOL);

    String NEEDLE_AUDIO_RECORDER_WORKER = "AudioRecorderTask";
    int NEEDLE_AUDIO_RECORDER_POOL = 1;
    BackgroundThreadExecutor AUDIO_RECORDER_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_AUDIO_RECORDER_WORKER)
            .withThreadPoolSize(Constants.NEEDLE_AUDIO_RECORDER_POOL);

    String NEEDLE_AUDIO_PLAYER_WORKER = "AudioPlayerTask";
    int NEEDLE_AUDIO_PLAYER_POOL = 1;
    BackgroundThreadExecutor AUDIO_PLAYER_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_AUDIO_PLAYER_WORKER)
            .withThreadPoolSize(Constants.NEEDLE_AUDIO_PLAYER_POOL);

    String NEEDLE_AUDIO_TASK = "AudioWorkerTask";
    int NEEDLE_AUDIO_TASK_POOL = 2;
    BackgroundThreadExecutor AUDIO_TASK_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_AUDIO_TASK)
            .withThreadPoolSize(Constants.NEEDLE_AUDIO_TASK_POOL);

    String NEEDLE_NETWORK_TASK = "NetworkTask";
    int NEEDLE_NETWORK_POOL = 10;
    BackgroundThreadExecutor NETWORK_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_NETWORK_TASK)
            .withThreadPoolSize(Constants.NEEDLE_NETWORK_POOL);

    String NEEDLE_ASYNC_TASK = "AsyncTask";
    int NEEDLE_ASYNC_POOL = Runtime.getRuntime().availableProcessors() * 2;
    BackgroundThreadExecutor ASYNC_TASK_EXECUTOR = Needle.onBackgroundThread()
            .withTaskType(Constants.NEEDLE_ASYNC_TASK)
            .withThreadPoolSize(Constants.NEEDLE_ASYNC_POOL);
}
