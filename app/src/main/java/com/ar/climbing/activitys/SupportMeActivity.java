package com.ar.climbing.activitys;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.ar.climbing.R;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class SupportMeActivity extends AppCompatActivity {
    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_me);

        adView = findViewById(R.id.adView);

        AdRequest.Builder adRequestBuilder = new AdRequest.Builder();
        adRequestBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);

        adView.loadAd(adRequestBuilder.build());
    }

    @Override
    public void onResume() {
        super.onResume();

        // Resume the AdView.
        adView.resume();
    }

    @Override
    public void onPause() {
        // Pause the AdView.
        adView.pause();

        super.onPause();
    }

    @Override
    public void onDestroy() {
        // Destroy the AdView.
        adView.destroy();

        super.onDestroy();
    }
}
