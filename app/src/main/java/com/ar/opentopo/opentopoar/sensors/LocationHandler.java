package com.ar.opentopo.opentopoar.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.service.autofill.FillEventHistory;
import android.support.v4.app.ActivityCompat;

import com.ar.opentopo.opentopoar.tools.EnvironmentHandler;

import java.util.EventListener;

/**
 * Created by xyz on 12/6/17.
 */

public class LocationHandler implements LocationListener {
    public static final int REQUEST_FINE_LOCATION_PERMISSION = 100;

    private LocationManager locationManager;
    private Activity activity;
    private Context context;
    private String provider;
    private EnvironmentHandler eventsHandler;

    public LocationHandler(LocationManager pLocationManager, Activity pActivity, Context pContext, EnvironmentHandler pEventsHandler) {
        this.activity = pActivity;
        this.context = pContext;
        this.locationManager = pLocationManager;
        this.eventsHandler = pEventsHandler;

        Criteria criteria = new Criteria();
        criteria.setAltitudeRequired(true);
        criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

        provider = locationManager.getBestProvider(criteria, false);
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_PERMISSION);
            return;
        }
    }

    public void onResume() {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_PERMISSION);
            return;
        }
        locationManager.requestLocationUpdates(provider, 200, 0.25f, this);
    }

    public void onPause() {
        locationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        eventsHandler.updatePosition((float)location.getLongitude(), (float)location.getLatitude(), (float)location.getAltitude(), location.getAccuracy());
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
