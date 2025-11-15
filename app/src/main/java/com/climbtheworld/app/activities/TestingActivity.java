package com.climbtheworld.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.climbtheworld.app.R;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;

public class TestingActivity extends AppCompatActivity {
	public static final int IMPORT_COUNTER = 5;
	MapView mapView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		MapLibre.getInstance(this);

		setContentView(R.layout.activity_testing);

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});

		// Init the MapView
		MapView mapView = findViewById(R.id.mapView);

		//https://openfreemap.org/quick_start/

		// The Kotlin lambda is replaced by an anonymous inner class implementing
		// OnMapReadyCallback
		mapView.getMapAsync(new OnMapReadyCallback() {
			@Override
			public void onMapReady(MapLibreMap maplibreMap) {
//				maplibreMap.setStyle("https://tiles.openfreemap.org/styles/liberty");
				maplibreMap.setStyle("https://demotiles.maplibre.org/terrain-tiles/tiles.json");

				// Build the CameraPosition
				CameraPosition position = new CameraPosition.Builder()
						.target(new LatLng(0.0, 0.0)) // Sets the new camera position
						.zoom(1.0) // Sets the zoom
						.build(); // Creates a CameraPosition from the builder

				maplibreMap.setCameraPosition(position);
			}
		});

	}

	@Override
	public void onStart() {
		super.onStart();
		if (mapView != null) {
			mapView.onStart();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mapView != null) {
			mapView.onResume();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mapView != null) {
			mapView.onPause();
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (mapView != null) {
			mapView.onStop();
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		if (mapView != null) {
			mapView.onLowMemory();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mapView != null) {
			mapView.onDestroy();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mapView != null) {
			mapView.onSaveInstanceState(outState);
		}
	}
}
