package com.climbtheworld.app.activitys;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.climbtheworld.app.R;

public class WalkieTalkieActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bluetoothMenu:
                Intent intentOpenBluetoothSettings = new Intent();
                intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intentOpenBluetoothSettings);
                break;

            case R.id.wifiMenu:
                final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity( intent);
                break;
        }
    }
}
