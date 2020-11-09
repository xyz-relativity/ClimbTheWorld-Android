package com.climbtheworld.app.utils;


import com.climbtheworld.app.converter.tools.GradeSystem;

import java.util.UUID;

import needle.BackgroundThreadExecutor;
import needle.Needle;

/**
 * Created by xyz on 12/27/17.
 */

public interface Constants {
	String[] uiaaGrades =   new String[]{"1-",  "1",   "1+",  "2-",  "2",   "2+",  "3-",  "3",   "3+",  "4-",   "4",    "4+",   "5-",     "5",     "5+",      "6-",    "6",      "6+",    "7-",    "7",     "7+",    "8-",    "8",     "8+",    "9-",    "9",     "9+",    "10-",   "10",     "10+",   "11-",   "11",     "11+",      "12+",     "12",    "12+",       "13-",    "13",    "13+",   "14-"};
	String[] ukTechGrades = new String[]{"1",   "1",   "1",   "2",   "2",   "2",   "3",   "3",   "3",   "4a",   "4a",   "4a",   "4a/4b",  "4b",    "4c",      "4c/5a", "5a",     "5a/5b", "5b",    "5b/5c", "5c",    "5c/6a", "6a",    "6a",    "6b",    "6b/6c", "6c",    "6c",    "6c/7a",  "7a",    "7a",    "7a/7b",  "7b",       "7b",      "7b",    ">7b",       ">7b",    ">7b",   ">7b",   ">7b"};
	String[] ukAdjGrades =  new String[]{"M",   "M",   "M",   "M/D", "M/D", "M/D", "D",   "D",   "D",   "D/VD", "D/VD", "VD",   "S",      "HS",    "HS/VS",   "VS",    "HVS",    "E1",    "E1/E2", "E2",    "E2/E3", "E3",    "E4",    "E4/E5", "E5",    "E6",    "E6/E7", "E7",    "E7/E8",  "E8",    "E9",    "E9/E10", "E10",      "E11",     "E11",   ">E11",      ">E11",   ">E11",  ">E11",  ">E11"};
	String[] fbGrades =     new String[]{"1",   "1",   "1",   "1",   "1",   "1",   "1/2", "1/2", "1/2", "2",    "2",    "2",    "2/3",    "3",     "4a",      "4a/4b", "4b",     "4c",    "5a",    "5b",    "5c",    "6a",    "6b",    "6b+",   "6c",    "6c+",   "7a",    "7a+",   "7a+/7b", "7b",    "7b+",   "7c",     "7c+",      "7c+/8a",  "8a",    "8a+/8b",    "8b",     "8b+",   "8c",    "8c+"};
	String[] frenchGrades = new String[]{"1",   "1",   "1",   "2",   "2",   "2",   "3",   "3",   "3",   "4",    "4",    "4+",   "5a",     "5a/5b", "5b",      "5b/5c", "5c",     "6a",    "6a+",   "6b",    "6b+",   "6c",    "7a",    "7a+",   "7b+",   "7c",    "7c+",   "8a",    "8a/8a+", "8a+",   "8b",    "8b+",    "8c",       "8c+",     "9a",    "9a+",       "9a+/9b", "9b",    "9b+",   "9c"};
	String[] saxonGrades =  new String[]{"I",   "I",   "I",   "II",  "II",  "II",  "III", "III", "III", "IV",   "IV",   "IV/V", "V",      "VI",    "VI/VIIa", "VIIa",  "VIIb",   "VIIc",  "VIIIa", "VIIIb", "VIIIc", "IXa",   "IXb",   "IXc",   "Xa",    "Xb",    "Xc",    "Xc",    "Xc/XIa", "XIa",   "XIb",   "XIc",    "XIc/XIIa", "XIIa",    "XIIb",  "XIIb/XIIc", "XIIc",   "XIIc",  ">XIIc", ">XIIc"};
	String[] nordicGrades = new String[]{"1",   "1",   "1",   "1",   "1",   "1",   "1/2", "1/2", "1/2", "2",    "2",    "2",    "2/3",    "3",     "4a",      "4a/4b", "4b",     "4c",    "5a",    "5b",    "5c",    "6a",    "6b",    "6b+",   "6c",    "6c+",   "7a",    "7a+",   "7a+/7b", "7b",    "7b+",   "7c",     "7c+",      "7c+/8a",  "8a",    "8a+/8b",    "8b",     "8b+",   "8c",    "8c+"};
	String[] ydsGrades =    new String[]{"5",   "5",   "5",   "5.1", "5.1", "5.2", "5.2", "5.3", "5.3", "5.4",  "5.5",  "5.6",  "5.7",    "5.8",   "5.9",     "5.10a", "5.10b",  "5.10c", "5.10d", "5.11a", "5.11b", "5.11c", "5.11d", "5.12a", "5.12b", "5.12c", "5.12d", "5.13a", "5.13b",  "5.13c", "5.13d", "5.14a",  "5.14b",    "5.14c",   "5.14d", "5.15a",     "5.15a",  "5.15b", "5.15c", "5.15d"};
	String[] vGradeGrades = new String[]{"VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-", "VB-",  "VB-",  "VB-",  "VB-/VB", "VB",    "VB/V0-",  "V0-",   "V0-/V0", "V0",    "V0+",   "V1",    "V1/V2", "V2",    "V3",    "V3/V4", "V4",    "V4/V5", "V5",    "V6",    "V6/V7",  "V7",    "V8",    "V9",     "V10",      "V10/V11", "V11",   "V12",       "V13",    "V14",   "V15",   "V15"};
	String[] wiGrades =     new String[]{"WI2", "WI2", "WI2", "WI2", "WI2", "WI2", "WI2", "WI3", "WI3", "WI3",  "WI3",  "WI3",  "WI3",    "WI4",   "WI5",     "WI6",   "WI6",    "WI6",   "WI6",   "WI7",   "WI7",   "WI8",   "WI8",   "WI8",   "WI8",   "WI9",   "WI9",   "WI9",   "WI9",    "WI10",  "WI10",  "WI10",   "WI10",     "WI11",    "WI11",  "WI11",      "WI11",   "WI12",  "WI13",  "WI13"};
	String[] mixedGrades =  new String[]{"M2",  "M2",  "M2",  "M2",  "M2",  "M2",  "M2",  "M3",  "M3",  "M3",   "M3",   "M3",   "M3",     "M4",    "M5",      "M6",    "M6",     "M6",    "M6",    "M7",    "M7",    "M8",    "M8",    "M8",    "M8",    "M9",    "M9",    "M9",    "M9",     "M10",   "M10",   "M10",    "M10",      "M11",     "M11",   "M11",       "M11",    "M12",   "M13",   "M13"};


	int TIME_TO_FRAME_MS = 20; //50fps

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
		OSM_0_6_API(
				"https://api.openstreetmap.org/api/0.6",
				"https://www.openstreetmap.org/"),
		OSM_SANDBOX_0_6_API(
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
	int OPEN_DOWNLOAD_ACTIVITY = 1002;
	int OPEN_OAUTH_ACTIVITY = 1003;

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
