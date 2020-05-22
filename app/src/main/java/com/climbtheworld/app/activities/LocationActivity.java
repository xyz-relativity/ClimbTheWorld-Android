package com.climbtheworld.app.activities;

import android.Manifest;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.ask.Ask;
import com.climbtheworld.app.sensors.ILocationListener;
import com.climbtheworld.app.sensors.IOrientationListener;
import com.climbtheworld.app.sensors.LocationManager;
import com.climbtheworld.app.sensors.OrientationManager;
import com.climbtheworld.app.utils.Globals;
import com.climbtheworld.app.widgets.CompassWidget;
import com.climbtheworld.app.widgets.MapViewWidget;
import com.climbtheworld.app.widgets.MapWidgetFactory;

import java.text.DecimalFormat;
import java.util.Locale;

public class LocationActivity extends AppCompatActivity implements ILocationListener, IOrientationListener {

    private LocationManager locationManager;
    private OrientationManager orientationManager;
    private MapViewWidget mapWidget;

    private static final int LOCATION_UPDATE = 500;
    private static final String COORD_VALUE = "%.6f";
    DecimalFormat decimalFormat = new DecimalFormat("000.00°");
    private TextView editLatitude;
    private TextView editLongitude;
    private TextView editElevation;
    private TextView editAzimuthName;
    private TextView editAzimuthValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        Ask.on(this)
                .id(500) // in case you are invoking multiple time Ask from same activity or fragment
                .forPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withRationales(getString(R.string.map_location_rational)) //optional
                .go();

        mapWidget = MapWidgetFactory.buildMapView(this);

        final CompassWidget compass = new CompassWidget(findViewById(R.id.compassButton));

        //location
        locationManager = new LocationManager(this, LOCATION_UPDATE);
        locationManager.addListener(this);

        orientationManager = new OrientationManager(this, SensorManager.SENSOR_DELAY_UI);
        orientationManager.addListener(this, compass);

        editLatitude = findViewById(R.id.editLatitude);
        editLongitude = findViewById(R.id.editLongitude);
        editElevation = findViewById(R.id.editElevation);
        editAzimuthName = findViewById(R.id.editAzimuthName);
        editAzimuthValue = findViewById(R.id.editAzimuthValue);
    }

    @Override
    public void updatePosition(double pDecLatitude, double pDecLongitude, double pMetersAltitude, double accuracy) {
        Globals.virtualCamera.updatePOILocation(pDecLatitude, pDecLongitude, pMetersAltitude);

        mapWidget.onLocationChange(Globals.poiToGeoPoint(Globals.virtualCamera));
        mapWidget.invalidate();
    }

    @Override
    public void updateOrientation(OrientationManager.OrientationEvent event) {
        Globals.virtualCamera.updateOrientation(event);

        mapWidget.onOrientationChange(event);
        mapWidget.invalidate();

        int azimuthID = (int) (int)Math.round((  ((double)event.global.x % 360) / 22.5)) % 16;
        editAzimuthName.setText(getResources().getStringArray(R.array.cardinal_names)[azimuthID]);

        editLatitude.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLatitude));
        editLongitude.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.decimalLongitude));
        editElevation.setText(String.format(Locale.getDefault(), COORD_VALUE, Globals.virtualCamera.elevationMeters));

        editAzimuthValue.setText(decimalFormat.format(event.global.x));
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
