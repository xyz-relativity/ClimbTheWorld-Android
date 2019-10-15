package com.climbtheworld.app.activities;

import android.Manifest;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationManager;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.CompassWidget;

import java.util.Locale;

public class LocationActivity extends AppCompatActivity implements ILocationListener, IOrientationListener {

    private LocationManager locationManager;
    private OrientationManager orientationManager;

    private static final int LOCATION_UPDATE = 500;
    private static final String AZIMUTH_VALUE = "%s (%3.4f°)";
    private static final String COORD_VALUE = "%.6f";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        Ask.on(this)
                .id(500) // in case you are invoking multiple time Ask from same activity or fragment
                .forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withRationales(getString(R.string.map_location_rational)) //optional
                .go();

        final CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));

        //location
        locationManager = new LocationManager(this, LOCATION_UPDATE);
        locationManager.addListener(this);

        orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
        orientationManager.addListener(this, compass);

    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);
    }

    @Override
    public void updateOrientation(double pAzimuth, double pPitch, double pRoll) {
        Globals.virtualCamera.updateOrientation(pAzimuth, pPitch, pRoll);

        int azimuthID = (int) Math.floor(Math.abs((360 - pAzimuth) - 11.25) / 22.5);
        ((TextView)findViewById(R.id.editLatitude)).setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLatitude));
        ((TextView)findViewById(R.id.editLongitude)).setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLongitude));
        ((TextView)findViewById(R.id.editElevation)).setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.elevationMeters));
        ((TextView)findViewById(R.id.editAzimuth)).setText(String.format(Locale.getDefault(), AZIMUTH_VALUE, getResources().getStringArray(R.array.cardinal_names)[azimuthID], Globals.virtualCamera.degAzimuth));
    }

    @Override
    protected void onResume() {
        super.onResume();

        Globals.onResume(this);

        locationManager.onResume();
        orientationManager.onResume();
    }

    @Override
    protected void onPause() {
        locationManager.onPause();
        orientationManager.onPause();

        Globals.onPause(this);

        super.onPause();
    }
}
