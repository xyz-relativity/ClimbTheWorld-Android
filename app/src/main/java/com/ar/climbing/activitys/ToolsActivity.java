package com.ar.climbing.activitys;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.ar.climbing.R;
import com.ar.climbing.utils.Configs;
import com.ar.climbing.utils.Globals;

public class ToolsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);
    }

    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.ButtonSettings:
                intent = new Intent(ToolsActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonDownload:
                intent = new Intent(ToolsActivity.this, NodesDataManagerActivity.class);
                startActivity(intent);
                break;

            case R.id.ButtonLicense:
                intent = new Intent(ToolsActivity.this, LicenseActivity.class);
                startActivity(intent);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Globals.globalConfigs.getBoolean(Configs.ConfigKey.keepScreenOn)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onPause() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onPause();
    }
}
