package com.climbtheworld.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Globals;

public class ToolsActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tools);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
	}

	public void onClick(View v) {
		Intent intent;
		int id = v.getId();
		if (id == R.id.buttonSettings) {
			intent = new Intent(ToolsActivity.this, SettingsActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonDownload) {
			intent = new Intent(ToolsActivity.this, NodesDataManagerActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonSearch) {
			intent = new Intent(ToolsActivity.this, SearchActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonLogBook) {
			intent = new Intent(ToolsActivity.this, LogBookActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonUnitConverter) {
			intent = new Intent(ToolsActivity.this, UnitsConverterActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonWalkieTalkie) {
			intent = new Intent(ToolsActivity.this, WalkieTalkieActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonSensors) {
			intent = new Intent(ToolsActivity.this, EnvironmentActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonTutorial) {
			intent = new Intent(ToolsActivity.this, FirstRunActivity.class);
			startActivity(intent);
		} else if (id == R.id.ButtonLicense) {
			intent = new Intent(ToolsActivity.this, LicenseActivity.class);
			startActivity(intent);
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
