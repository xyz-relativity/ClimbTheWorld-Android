package com.climbtheworld.app.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.BuildConfig;
import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.storage.DataManagerNew;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.utils.constants.Constants;

import org.json.JSONException;
import org.osmdroid.config.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends AppCompatActivity {
	int importCounter = ImporterActivity.IMPORT_COUNTER;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// This call has to be the first call of the application
		initializeGlobals();

		//debug code:
		Constants.ASYNC_TASK_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				try {
					InputStream inputStream = getResources().openRawResource(R.raw.ca);

					BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
					StringBuilder line = new StringBuilder();

					while (reader.ready()) {
						line.append(reader.readLine());
					}
					DataManagerNew dataManager = new DataManagerNew();
					dataManager.parseOsmJsonString(MainActivity.this, line.toString(), "CA");

				} catch (IOException | JSONException e) {
					throw new RuntimeException(e);
				}
			}
		});

		((TextView) findViewById(R.id.textVersionString)).setText(getString(R.string.version, Globals.versionName));

		// if we receive an external app event to open a location. go directly to map.
		Intent intent = getIntent();
		Uri data = intent.getData();
		if (data != null) {
			List<String> segments = data.getPathSegments();
			if (segments.get(0).equalsIgnoreCase("location") && segments.size() == 2) {
				Intent mapIntent = new Intent(this, MapActivity.class);
				mapIntent.putExtra("GeoPoint", segments.get(1));
				this.startActivity(mapIntent);
			}
		}

		if (Configs.instance(MainActivity.this).getBoolean(Configs.ConfigKey.showHardwareLimitation) &&
				((SensorManager) getSystemService(SENSOR_SERVICE)).getSensorList(Sensor.TYPE_GYROSCOPE).size() == 0) {
			new AlertDialog.Builder(this)
					.setCancelable(false) // This blocks the 'BACK' button
					.setTitle(getResources().getString(R.string.gyroscope_missing))
					.setMessage(getResources().getString(R.string.gyroscope_missing_message))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					})
					.setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Configs.instance(MainActivity.this).setBoolean(Configs.ConfigKey.showHardwareLimitation, false);
						}
					}).show();
		}

		if (Configs.instance(this).getBoolean(Configs.ConfigKey.isFirstRun)) {
			Globals.showDownloadPopup = false;
			Configs.instance(this).setBoolean(Configs.ConfigKey.isFirstRun, false);
			Intent firstRunIntent = new Intent(MainActivity.this, FirstRunActivity.class);
			startActivity(firstRunIntent);
		}

		setupButtonEvents();
	}

	private void setupButtonEvents() {
		findViewById(R.id.ButtonAR).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, AugmentedRealityActivity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.ButtonViewMap).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, MapActivity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.ButtonTools).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, ToolsActivity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.searchView).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, SearchActivity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.ButtonDonate).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, SupportMeActivity.class);
				startActivity(intent);
			}
		});

		findViewById(R.id.textVersionString).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				importCounter--;
				if (importCounter <= 0) {
					Intent intent = new Intent(MainActivity.this, ImporterActivity.class);
					startActivity(intent);
					importCounter = ImporterActivity.IMPORT_COUNTER;
				}
			}
		});
	}

	private synchronized void initializeGlobals() {
		try {
			PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
			Globals.versionName = pInfo.versionName;
		} catch (PackageManager.NameNotFoundException ignore) {
		}
		//use private storage for ASM cache to avoid the need for external storage permissions.
		Configuration.getInstance().setOsmdroidBasePath(getFilesDir().getAbsoluteFile());
		Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);
		importCounter = ImporterActivity.IMPORT_COUNTER;
	}

	@Override
	protected void onPause() {
		Globals.onPause(this);

		super.onPause();
	}
}
