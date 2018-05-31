package com.climbtheworld.app.activitys;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;

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

        Globals.onResume(this);
    }

    @Override
    protected void onPause() {
        Globals.onPause(this);

        super.onPause();
    }
}
