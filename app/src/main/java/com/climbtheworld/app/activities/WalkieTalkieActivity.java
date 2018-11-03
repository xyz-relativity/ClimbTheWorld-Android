package com.climbtheworld.app.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.networking.BluetoothNetworkClient;
import com.climbtheworld.app.networking.DeviceInfo;
import com.climbtheworld.app.networking.voicetools.BasicVoiceDetector;
import com.climbtheworld.app.networking.voicetools.IRecordingListener;
import com.climbtheworld.app.networking.voicetools.IVoiceDetector;
import com.climbtheworld.app.networking.voicetools.RecordingThread;
import com.climbtheworld.app.utils.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class WalkieTalkieActivity extends AppCompatActivity {

    private static final UUID MY_UUID = UUID.fromString("cc55c6f1-74e3-418f-a110-84cb33733c6b");

    double lastPeak = 0f;
    private ProgressBar energyDisplay;
    private ImageView mic;
    private Button ptt;
    private BluetoothAdapter mBluetoothAdapter;
    private LayoutInflater inflater;
    ArrayList<DeviceInfo> deviceList;
    List<BluetoothSocket> activeInSockets = new LinkedList<>();
    List<BluetoothSocket> activeOutSockets = new LinkedList<>();

    private final int DISABLED_MIC_COLOR = Color.argb(200, 255, 255, 255);
    private final int BROADCASTING_MIC_COLOR = Color.argb(200, 0, 255, 0);
    private final int HANDSFREE_MIC_COLOR = Color.argb(200, 255, 255, 0);

    private RecordingThread recordingThread;

    private boolean isKeyPressed = false;
    private boolean isHandsfree = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        energyDisplay = findViewById(R.id.progressBar);
        mic = findViewById(R.id.microphoneIcon);
        ptt = findViewById(R.id.pushToTalkButton);

        Button ptt = findViewById(R.id.pushToTalkButton);
        ptt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        toggleBroadcast(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        toggleBroadcast(false);
                        break;
                }
                return false;
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
                toggleBroadcast(!isKeyPressed);
                isKeyPressed = !isKeyPressed;
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleBroadcast(boolean on) {
        if (isHandsfree) {
            return;
        }

        if (on) {
            startBroadcast();
        } else {
            stopBroadcast();
        }
    }

    private void startBroadcast() {
        mic.setColorFilter(BROADCASTING_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
        connectBluetoothClients();

        recordingThread = new RecordingThread(new IRecordingListener() {
            @Override
            public void onRecordingStarted() {

            }

            @Override
            public void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms) {
//                sendData(frame);
                updateEnergy(energy);
            }

            @Override
            public void onRecordingDone() {
                energyDisplay.setProgress(0);
            }
        });
        Constants.AUDIO_EXECUTOR
                .execute(recordingThread);
    }

    private void stopBroadcast() {
        mic.setColorFilter(DISABLED_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
        recordingThread.stopRecording();

        startBluetoothListener();
    }

    private void startHandsFree() {
        ptt.setVisibility(View.INVISIBLE);
        isHandsfree = true;
        mic.setColorFilter(HANDSFREE_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
        connectBluetoothClients();

        recordingThread = new RecordingThread(new IRecordingListener() {
            IVoiceDetector voice = new BasicVoiceDetector();
            boolean state = false;

            @Override
            public void onRecordingStarted() {

            }

            @Override
            public void onAudio(byte[] frame, int numberOfReadBytes, double energy, double rms) {
                if (voice.onAudio(frame, numberOfReadBytes, rms)) {
//                    sendData(frame);
                    updateEnergy(energy);
                    if (!state) {
                        state = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mic.setColorFilter(BROADCASTING_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                            }
                        });
                    }
                } else {
                    updateEnergy(0);
                    if (state) {
                        state = false;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mic.setColorFilter(HANDSFREE_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                            }
                        });
                    }
                }
            }

            @Override
            public void onRecordingDone() {
                energyDisplay.setProgress(0);
            }
        });
        Constants.AUDIO_EXECUTOR
                .execute(recordingThread);
    }

    private void sendData(byte[] frame) {
        for (BluetoothSocket socket: activeOutSockets) {
            if (socket.isConnected()) {
                try {
                    socket.getOutputStream().write(frame);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void stopHandsFree() {
        ptt.setVisibility(View.VISIBLE);
        isHandsfree = true;
        recordingThread.stopRecording();
        mic.setColorFilter(DISABLED_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
    }

    private void updateEnergy(double energy) {
        double peak = energy;
        if(lastPeak > peak) {
            peak = lastPeak * 0.575f;
        }

        lastPeak = peak;

        final double displayPeak = peak;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                energyDisplay.setProgress((int) (displayPeak * 100));
            }
        });
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
                    startHandsFree();
                } else {
                    stopHandsFree();
                }
                break;
        }
    }
}
