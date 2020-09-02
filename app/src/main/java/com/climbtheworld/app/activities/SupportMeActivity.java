package com.climbtheworld.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.map.marker.MarkerUtils;

import androidx.appcompat.app.AppCompatActivity;

public class SupportMeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support_me);

        ((TextView) findViewById(R.id.osmText)). setText(Html.fromHtml(getString(R.string.osm_text, getString(R.string.app_name))));
        ((TextView) findViewById(R.id.patreonText)). setText(Html.fromHtml(getString(R.string.patreon_text, getString(R.string.app_name))));
        ((TextView) findViewById(R.id.liberapayText)). setText(Html.fromHtml(getString(R.string.liberapay_text, getString(R.string.app_name))));
        ((TextView) findViewById(R.id.paypalText)). setText(Html.fromHtml(getString(R.string.paypal_text, getString(R.string.app_name))));

        ((ImageView) findViewById(R.id.contributeRouteIcon)).setImageDrawable(MarkerUtils.getLayoutIcon(this, R.layout.icon_node_add_display, 255));
    }

    public void onClick(View v) {
        Intent browserIntent;
        switch (v.getId()) {
            case R.id.osmButton:
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/"));
                startActivity(browserIntent);
                break;

            case R.id.contributeRouteButton:
                Intent intent = new Intent(this, EditNodeActivity.class);
                startActivity(intent);
                break;

            case R.id.patreonButton:
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.patreon.com/climb_the_world/overview"));
                startActivity(browserIntent);
                break;

            case R.id.liberapayButton:
                browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://liberapay.com/xyz.relativity/donate"));
                startActivity(browserIntent);
                break;

            case R.id.paypalButton:

                break;
        }
    }
}
