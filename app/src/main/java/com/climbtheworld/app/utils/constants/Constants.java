package com.climbtheworld.app.utils.constants;


import needle.BackgroundThreadExecutor;
import needle.Needle;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
	int TIME_TO_FRAME_MS = 5;

	int MINIMUM_CHECK_INTERVAL_MILLISECONDS = 10000;

	long HTTP_TIMEOUT_SECONDS = 120;

	int POS_UPDATE_ANIMATION_STEPS = 10;
	OSM_API DEFAULT_API = OSM_API.OSM_0_6_API;
	//OpenStreetMaps Overpass:
	String[] OVERPASS_API = {
			"https://overpass-api.de/api/interpreter",
//            "https://overpass.kumi.systems/api/interpreter"
	};
	//Activity events
	int OPEN_EDIT_ACTIVITY = 1001;
	int OPEN_TOOLS_ACTIVITY = 1002;
	int OPEN_OAUTH_ACTIVITY = 1003;
	//general worker pool for async tasks.
	String NEEDLE_ASYNC_TASK = "AsyncTask";
	int NEEDLE_ASYNC_POOL = Runtime.getRuntime().availableProcessors() * 4;
	BackgroundThreadExecutor ASYNC_TASK_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_ASYNC_TASK)
			.withThreadPoolSize(Constants.NEEDLE_ASYNC_POOL);
	//Needle task pools
	//used for map refresh work
	String NEEDLE_MAP_TASK = "mapTask";
	int NEEDLE_MAP_POOL = 1;
	BackgroundThreadExecutor MAP_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_MAP_TASK)
			.withThreadPoolSize(Constants.NEEDLE_MAP_POOL);
	//use for AR work
	String NEEDLE_AR_TASK = "arTask";
	int NEEDLE_AR_POOL = 1;
	BackgroundThreadExecutor AR_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_AR_TASK)
			.withThreadPoolSize(Constants.NEEDLE_AR_POOL);
	//use mainly for map write tasks, but some read tasks as well.
	String NEEDLE_DB_TASK = "dbTask";
	int NEEDLE_DB_POOL = 1;
	BackgroundThreadExecutor DB_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_DB_TASK)
			.withThreadPoolSize(Constants.NEEDLE_DB_POOL);
	//used to download an upload data
	String NEEDLE_WEB_TASK = "webTask";
	int NEEDLE_WEB_POOL = 2;
	BackgroundThreadExecutor WEB_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_WEB_TASK)
			.withThreadPoolSize(Constants.NEEDLE_WEB_POOL);
	//micophone handling task
	String NEEDLE_AUDIO_RECORDER_WORKER = "AudioRecorderTask";
	int NEEDLE_AUDIO_RECORDER_POOL = 1;
	BackgroundThreadExecutor AUDIO_RECORDER_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_AUDIO_RECORDER_WORKER)
			.withThreadPoolSize(Constants.NEEDLE_AUDIO_RECORDER_POOL);
	//audio processing pool
	String NEEDLE_AUDIO_TASK = "AudioWorkerTask";
	int NEEDLE_AUDIO_TASK_POOL = 2;
	BackgroundThreadExecutor AUDIO_TASK_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_AUDIO_TASK)
			.withThreadPoolSize(Constants.NEEDLE_AUDIO_TASK_POOL);
	//used for walkie=talkye network communication.
	String NEEDLE_NETWORK_TASK = "NetworkTask";
	int NEEDLE_NETWORK_POOL = 4;
	BackgroundThreadExecutor NETWORK_EXECUTOR = Needle.onBackgroundThread()
			.withTaskType(Constants.NEEDLE_NETWORK_TASK)
			.withThreadPoolSize(Constants.NEEDLE_NETWORK_POOL);
	//OpenStreetMaps
	enum OSM_API {
		OSM_0_6_API(
				"https://api.openstreetmap.org/api/0.6",
				"https://www.openstreetmap.org/"),
		OSM_SANDBOX_0_6_API(
				"https://master.apis.dev.openstreetmap.org/api/0.6",
				"https://master.apis.dev.openstreetmap.org/");

		public String apiUrl;
		public String oAuthUrl;
		OSM_API(String apiUrl, String oAuthUrl) {
			this.apiUrl = apiUrl;
			this.oAuthUrl = oAuthUrl;
		}
	}
}
