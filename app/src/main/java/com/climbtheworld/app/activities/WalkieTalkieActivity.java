package com.climbtheworld.app.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Switch;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.networking.UiNetworkManager;
import com.climbtheworld.app.intercon.states.HandsfreeState;
import com.climbtheworld.app.intercon.states.IInterconState;
import com.climbtheworld.app.intercon.states.InterconState;
import com.climbtheworld.app.intercon.states.PushToTalkState;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Globals;
import java.net.SocketException;

public class WalkieTalkieActivity extends AppCompatActivity {
    private IInterconState activeState;
    private UiNetworkManager networkManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        try {
            networkManager = new UiNetworkManager(this);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        PowerManager pm = (PowerManager) getSystemService(WalkieTalkieActivity.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app:intercon");
        wakeLock.acquire();
    }

    @Override
    protected  void onStart() {
        super.onStart();
        networkManager.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((EditText)findViewById(R.id.editCallsign)).setText(Globals.globalConfigs.getString(Configs.ConfigKey.callsign));
        updateState();
        networkManager.onResume();
    }

    @Override
    protected void onPause() {
        networkManager.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        networkManager.onDestroy();
        activeState.finish();
        super.onDestroy();
    }

    private void updateState() {
        Switch handsFree = findViewById(R.id.handsFreeSwitch);
        if (activeState!= null) {
            activeState.finish();
        }

        if (handsFree.isChecked()) {
            findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
            activeState = new HandsfreeState(this);
        } else {
            findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
            activeState = new PushToTalkState(this);
        }
        ((InterconState)activeState).addListener(networkManager);
    }

    public void onClick(View v) {
        //Creating the instance of PopupMenu
        PopupMenu popup = new PopupMenu(WalkieTalkieActivity.this, v);
        switch (v.getId()) {
            case R.id.bluetoothMenu:
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.bluetooth_options, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.bluetoothSettings:
                                Intent intentOpenBluetoothSettings = new Intent();
                                intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                                startActivity(intentOpenBluetoothSettings);
                                return true;
                        }
                        return false;
                    }
                });
                popup.show();//showing popup menu
                break;

            case R.id.wifiMenu:
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.wifi_options, popup.getMenu());

                //registering popup with OnMenuItemClickListener
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.wifiSettings:
                                Intent intentOpenBluetoothSettings = new Intent();
                                intentOpenBluetoothSettings.setAction(Settings.ACTION_WIFI_SETTINGS);
                                startActivity(intentOpenBluetoothSettings);
                                return true;
                            case R.id.hotspotWifiSettings:
                                final Intent intent = new Intent(Intent.ACTION_MAIN, null);
                                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                                final ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                                intent.setComponent(cn);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity( intent);
                                return true;
                        }
                        return false;
                    }
                });
                popup.show();//showing popup menu
                break;

            case R.id.buttonSettings:
                Intent intent = new Intent(WalkieTalkieActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;

            case R.id.handsFreeSwitch:
                updateState();
                break;
        }
    }
}
