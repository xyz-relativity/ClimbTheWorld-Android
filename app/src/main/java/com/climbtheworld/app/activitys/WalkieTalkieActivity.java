package com.climbtheworld.app.activitys;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalkieTalkieActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 16000;
    private static final UUID MY_UUID = UUID.fromString("cc55c6f1-74e3-418f-a110-84cb33733c6b");

    private Runnable recordingThread;
    private Runnable playThread;
    private Runnable networkThread;

    private AudioRecord recorder = null;
    private AudioTrack playback = null;
    private byte buffer[] = null;
    private byte playBuffer[] = null;
    private int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private int bufferSize = minSize;
    private boolean isRecording = false;
    private ProgressBar energyDisplay;
    private ImageView mic;
    private BluetoothAdapter mBluetoothAdapter;
    private LayoutInflater inflater;
    ArrayList<DeviceInfo> deviceList;
    List<BluetoothSocket> activeInSockets = new LinkedList<>();
    List<BluetoothSocket> activeOutSockets = new LinkedList<>();

    final ExecutorService producers = Executors.newFixedThreadPool(1);
    final ExecutorService consumers = Executors.newFixedThreadPool(5);

    private final int DISABLED_MIC_COLOR = Color.argb(200, 255, 255, 255);
    private final int BROADCASTING_MIC_COLOR = Color.argb(200, 0, 255, 0);
    private final int HANDSFREE_MIC_COLOR = Color.argb(200, 255, 255, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        energyDisplay = findViewById(R.id.progressBar);
        mic = findViewById(R.id.microphoneIcon);

        Button ptt = findViewById(R.id.pushToTalkButton);
        ptt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        mic.setColorFilter(BROADCASTING_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                        stopPlaying();
                        startRecording();
                        break;
                    case MotionEvent.ACTION_UP:
                        mic.setColorFilter(DISABLED_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                        stopRecording();
                        startPlaying();
                        break;
                }
                return false;
            }
        });

        initAudioSystem();
        initRunnable();
    }

    private void initRunnable() {
        recordingThread = new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                buffer = new byte[bufferSize];
                sendRecording();
            }

            // Method for sending Audio
            private void sendRecording() {
                // Infinite loop until microphone button is released
                float[] samples = new float[bufferSize / 2];
                float lastPeak = 0f;

                connectBluetoothClients();
                // Start Recording
                recorder.startRecording();

                while (isRecording) {
                    int numberOfShort = recorder.read(buffer, 0, bufferSize);

                    consumers.submit(networkThread);
                    // convert bytes to samples here
                    for(int i = 0, s = 0; i < numberOfShort;) {
                        int sample = 0;

                        sample |= buffer[i++] & 0xFF; // (reverse these two lines
                        sample |= buffer[i++] << 8;   //  if the format is big endian)

                        // normalize to range of +/-1.0f
                        samples[s++] = sample / 32768f;
                    }

                    float rms = 0f;
                    float peak = 0f;
                    for(float sample : samples) {

                        float abs = Math.abs(sample);
                        if(abs > peak) {
                            peak = abs;
                        }

                        rms += sample * sample;
                    }

                    rms = (float)Math.sqrt(rms / samples.length);

                    if(lastPeak > peak) {
                        peak = lastPeak * 0.575f;
                    }

                    lastPeak = peak;

                    energyDisplay.setProgress((int)(peak*100));
                }
                energyDisplay.setProgress(0);
            }
        };

        playThread = new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                receiveRecording();
            }

            // Receive audio and write into audio track object for playback
            private void receiveRecording() {
                while (!isRecording) {
                    try {
                        for (BluetoothSocket socket : activeInSockets) {
                            InputStream inStream = socket.getInputStream();
                            if (inStream != null && inStream.available() != 0) {
                                inStream.read(playBuffer);
                                playback.write(playBuffer, 0, playBuffer.length);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        networkThread = new Runnable() {
            @Override
            public void run() {
                for (BluetoothSocket socket : activeOutSockets) {
                    if (socket.isConnected()) {
                        try {
                            socket.getOutputStream().write(buffer);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };
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
            final View newViewElement = inflater.inflate(R.layout.walkie_list_element, bluetoothListView, false);
            ((TextView)newViewElement.findViewById(R.id.deviceName)).setText(info.getName());
            ((TextView)newViewElement.findViewById(R.id.deviceAddress)).setText(info.getAddress());
            bluetoothListView.addView(newViewElement);
        }
    }

    private void initAudioSystem() {
        // Audio record object
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        // Audio track object
        playback = new AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minSize, AudioTrack.MODE_STREAM);
    }

    private void startBluetoothListener() {
        (new Thread() {
            public void run() {
                try {
                    BluetoothServerSocket socket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("xyz", MY_UUID);
                    activeInSockets.clear();
                    while (!isRecording) {
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

    public void stopRecording() {
        isRecording = false;
        if (recorder != null) {
            recorder.stop();
        }

        startBluetoothListener();
    }

    public void startRecording() {
        isRecording = true;
        producers.submit(recordingThread);
    }

    // Playback received audio
    public void startPlaying() {
        // Receive Buffer
        playBuffer = new byte[minSize];

        playback.play();
        producers.submit(playThread);
    }

    // Stop playing and free up resources
    public void stopPlaying() {
        isRecording = true;
        if (playback != null) {
            playback.stop();
        }
    }

    @Override
    protected void onDestroy()
    {
        //Release resources for audio objects
        recorder.release();
        playback.release();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initBluetoothDevices();
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
                    findViewById(R.id.pushToTalkButton).setVisibility(View.INVISIBLE);
                    mic.setColorFilter(HANDSFREE_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                } else {
                    findViewById(R.id.pushToTalkButton).setVisibility(View.VISIBLE);
                    mic.setColorFilter(DISABLED_MIC_COLOR, android.graphics.PorterDuff.Mode.MULTIPLY);
                }
                break;
        }
    }
}
