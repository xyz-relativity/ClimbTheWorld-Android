package com.climbtheworld.app.sensors;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xyz on 12/6/17.
 */

public class LocationHandler implements LocationListener, OnSuccessListener<Location> {
    public static final int REQUEST_FINE_LOCATION_PERMISSION = 100;

    public static final int LOCATION_MAX_UPDATE_INTERVAL = 5000;
    public static final int LOCATION_FASTEST_UPDATE_INTERVAL = 1000;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private LocationRequest mLocationRequest = new LocationRequest();
    private Activity activity;
    private Context context;
    private List<ILocationListener> eventsHandler = new ArrayList<>();

    public LocationHandler(Activity pActivity, Context pContext) {
        this.activity = pActivity;
        this.context = pContext;

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
        if (ActivityCompat.checkSelfPermission(pActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation().addOnSuccessListener(activity, this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    onLocationChanged(location);
                }
            }
        };

        mLocationRequest.setInterval(LOCATION_MAX_UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(LOCATION_FASTEST_UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    public void addListener(ILocationListener... pEventsHandler) {
        for (ILocationListener i : pEventsHandler) {
            if (!eventsHandler.contains(i)) {
                eventsHandler.add(i);
            }
        }

    }

    public void onResume() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null /* Looper */);
    }

    public void onPause() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
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

    @Override
    public void onSuccess(Location location) {
        // Got last known location. In some rare situations this can be null.
        if (location != null) {
            onLocationChanged(location);
        }
    }
}
