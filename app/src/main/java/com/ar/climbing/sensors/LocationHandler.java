package com.ar.climbing.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

import com.ar.climbing.utils.ILocationListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 12/6/17.
 */

public class LocationHandler implements LocationListener {
    public static final int REQUEST_FINE_LOCATION_PERMISSION = 100;

    public static final int LOCATION_MINIMUM_UPDATE_INTERVAL = 10000;
    private static final float LOCATION_MINIMUM_DISTANCE_METERS = 1f;

    private LocationManager locationManager;
    private Activity activity;
    private Context context;
    private String provider;
    private List<ILocationListener> eventsHandler = new ArrayList<>();

    public LocationHandler(LocationManager pLocationManager, Activity pActivity, Context pContext) {
        this.activity = pActivity;
        this.context = pContext;
        this.locationManager = pLocationManager;

        Criteria criteria = new Criteria();
        criteria.setAltitudeRequired(true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

        provider = locationManager.getBestProvider(criteria, false);
    }

    public void addListener(ILocationListener... pEventsHandler) {
        for (ILocationListener i: pEventsHandler) {
            if (!eventsHandler.contains(i)) {
                eventsHandler.add(i);
            }
        }

    }

    public void onResume() {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_PERMISSION);
            return;
        }
        locationManager.requestLocationUpdates(provider, LOCATION_MINIMUM_UPDATE_INTERVAL, LOCATION_MINIMUM_DISTANCE_METERS, this);
    }

    public void onPause() {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        double elev = location.getAltitude();
        double accuracy = location.getAccuracy();

        for (ILocationListener client : eventsHandler) {
            client.updatePosition(lat, lon, elev, accuracy);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
