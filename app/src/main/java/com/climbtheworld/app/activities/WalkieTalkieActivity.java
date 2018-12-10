package com.climbtheworld.app.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.BluetoothNetworkClient;
import com.climbtheworld.app.intercon.DeviceInfo;
import com.climbtheworld.app.intercon.states.HandsfreeState;
import com.climbtheworld.app.intercon.states.IInterconState;
import com.climbtheworld.app.intercon.states.PushToTalkState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class WalkieTalkieActivity extends AppCompatActivity {
    private IInterconState activeState;

    private static final UUID MY_UUID = UUID.fromString("cc55c6f1-74e3-418f-a110-84cb33733c6b");

    private BluetoothAdapter mBluetoothAdapter;
    private LayoutInflater inflater;
    ArrayList<DeviceInfo> deviceList;
    List<BluetoothSocket> activeInSockets = new LinkedList<>();
    List<BluetoothSocket> activeOutSockets = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        activeState = new PushToTalkState(this);
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private void startBluetoothListener() {
        (new Thread() {
            public void run() {
                try {
                    if (mBluetoothAdapter != null) {
                        BluetoothServerSocket socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("xyz", MY_UUID);
                        activeInSockets.clear();
                        activeInSockets.add(socket.accept());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private void connectBluetoothClients() {
        activeOutSockets.clear();

        for (DeviceInfo device: deviceList) {
            if (device.getClient() != null) {
                try {
                    BluetoothSocket btSocket = device.getClient().getDevice().createRfcommSocketToServiceRecord(MY_UUID);
                    if (btSocket != null) {
                        activeOutSockets.add(btSocket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initBluetoothDevices();
    }

    @Override
    protected void onPause() {
        activeState.finish();
        super.onPause();
    }

    private void initBluetoothDevices() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = new ArrayList<>();
        if (mBluetoothAdapter != null) {
            for (BluetoothDevice device: mBluetoothAdapter.getBondedDevices())
            {
                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.PHONE) {
                    DeviceInfo newDevice = new DeviceInfo(device.getName(), device.getAddress(), new BluetoothNetworkClient(device));
                    deviceList.add(newDevice);
                }
            }
        }

        // No devices found
        if (deviceList.size() == 0) {
            deviceList.add(new DeviceInfo(getString(R.string.no_clients_found), "", null));
        }

        LinearLayout bluetoothListView = findViewById(R.id.bluetoothClients);

        bluetoothListView.removeAllViews();
        for (DeviceInfo info: deviceList) {
            final View newViewElement = inflater.inflate(R.layout.list_item_walkie, bluetoothListView, false);
            ((TextView)newViewElement.findViewById(R.id.deviceName)).setText(info.getName());
            ((TextView)newViewElement.findViewById(R.id.deviceAddress)).setText(info.getAddress());
            bluetoothListView.addView(newViewElement);
        }
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

            case R.id.handsFreeSwitch:
                Switch handsFree = findViewById(R.id.handsFreeSwitch);
                if (handsFree.isChecked()) {
                    findViewById(R.id.pushToTalkButton).setVisibility(View.GONE);
                    activeState.finish();
                    activeState = new HandsfreeState(this);
                } else {
                    findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
                    activeState.finish();
                    activeState = new PushToTalkState(this);
                }
                break;
        }
    }
}
