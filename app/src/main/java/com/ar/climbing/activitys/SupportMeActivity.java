package com.ar.climbing.activitys;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import com.ar.climbing.R;
import com.google.android.gms.ads.AdView;

public class SupportMeActivity extends AppCompatActivity {
    private AdView adView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_me);

        ((TextView) findViewById(R.id.osmText)). setText(Html.fromHtml(getString(R.string.osm_text, getString(R.string.app_name))));
        ((TextView) findViewById(R.id.patreonText)). setText(Html.fromHtml(getString(R.string.patreon_text, getString(R.string.app_name))));
        ((TextView) findViewById(R.id.paypalText)). setText(Html.fromHtml(getString(R.string.paypal_text, getString(R.string.app_name))));
    }

    public void onClick(View v) {
        Intent browserIntent;
        switch (v.getId()) {
            case R.id.osmButton:
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/"));
                startActivity(browserIntent);
                break;

            case R.id.patreonButton:
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/user?u=11330081"));
                startActivity(browserIntent);
                break;

            case R.id.paypalButton:

                break;
        }
    }
}
