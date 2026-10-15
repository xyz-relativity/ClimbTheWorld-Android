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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.configs.Configs;
import com.climbtheworld.app.sensors.orientation.OrientationManager;
import com.climbtheworld.app.utils.Globals;

import java.util.List;

public class MainActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});

		// This call has to be the first call of the application
		initializeGlobals();

		((TextView) findViewById(R.id.textVersionString)).setText(
				getString(R.string.version, Globals.versionName));

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

		Configs configs = Configs.instance(this);

		if (configs.getBoolean(Configs.ConfigKey.showHardwareLimitation)) {
			// Without a gyroscope the compass still works, only slower and less steady, while
			// without a compass nothing can follow the way the device is facing.
			if (!OrientationManager.hasCompass(this)) {
				showHardwareLimitation(R.string.compass_missing, R.string.compass_missing_message);
			} else if (((SensorManager) getSystemService(SENSOR_SERVICE)).getSensorList(
					Sensor.TYPE_GYROSCOPE).isEmpty()) {
				showHardwareLimitation(R.string.gyroscope_missing,
						R.string.gyroscope_missing_message);
			}
		}

		if (configs.getBoolean(Configs.ConfigKey.isFirstRun)) {
			Globals.showDownloadPopup = false;
			configs.setBoolean(Configs.ConfigKey.isFirstRun, false);
			Intent firstRunIntent = new Intent(MainActivity.this, FirstRunActivity.class);
			startActivity(firstRunIntent);
		}

		setupButtonEvents();
	}

	private void showHardwareLimitation(int title, int message) {
		new AlertDialog.Builder(this)
				.setCancelable(false) // This blocks the 'BACK' button
				.setTitle(getResources().getString(title))
				.setMessage(getResources().getString(message))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				})
				.setNeutralButton(R.string.dont_show_again,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								Configs.instance(MainActivity.this)
										.setBoolean(Configs.ConfigKey.showHardwareLimitation,
												false);
							}
						}).show();
	}

	private void setupButtonEvents() {
		findViewById(R.id.ButtonAR).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// The A.R. view places everything by the way the device is facing.
				if (!OrientationManager.hasCompass(MainActivity.this)) {
					new AlertDialog.Builder(MainActivity.this)
							.setTitle(R.string.compass_missing)
							.setMessage(R.string.compass_missing_message)
							.setPositiveButton(android.R.string.ok, null)
							.show();
					return;
				}
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
	}

	private synchronized void initializeGlobals() {
		try {
			PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
			Globals.versionName = pInfo.versionName;
		} catch (PackageManager.NameNotFoundException ignore) {
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		Globals.onResume(this);
	}

	@Override
	protected void onPause() {
		Globals.onPause(this);

		super.onPause();
	}
}
