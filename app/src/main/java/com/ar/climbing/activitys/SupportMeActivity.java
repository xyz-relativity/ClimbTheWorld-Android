package com.ar.climbing.activitys;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.ar.climbing.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class SupportMeActivity extends AppCompatActivity {
    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_me);

        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();

        // Create an ad request.
        AdRequest.Builder adRequestBuilder = new AdRequest.Builder();

        // Optionally populate the ad request builder.
        adRequestBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);

        // Start loading the ad.
        mAdView.loadAd(adRequestBuilder.build());
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resume the AdView.
        mAdView.resume();
    }

    @Override
    public void onPause() {
        // Pause the AdView.
        mAdView.pause();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        // Destroy the AdView.
        mAdView.destroy();

        super.onDestroy();
    }
}
