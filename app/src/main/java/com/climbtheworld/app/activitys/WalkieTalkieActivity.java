package com.climbtheworld.app.activitys;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import com.climbtheworld.app.R;
import com.climbtheworld.app.utils.DeviceInfo;

import java.util.ArrayList;

public class WalkieTalkieActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 16000;

    private Thread recordingThread = null;
    private Thread playThread = null;
    private AudioRecord recorder = null;
    private AudioTrack playback = null;
    private byte buffer[] = null;
    private int minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private int bufferSize = minSize;
    private boolean isRecording = false;
    private ProgressBar energyDisplay;
    private BluetoothAdapter mBluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walkie_talkie);

        energyDisplay = findViewById(R.id.progressBar);

        Button ptt = findViewById(R.id.pushToTalkButton);
        ptt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ImageView mic = findViewById(R.id.microphoneIcon);
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        mic.setColorFilter(Color.argb(200, 0, 255, 0),android.graphics.PorterDuff.Mode.MULTIPLY);
                        startRecording();
                        break;
                    case MotionEvent.ACTION_UP:
                        mic.setColorFilter(Color.argb(200, 255, 255, 255),android.graphics.PorterDuff.Mode.MULTIPLY);
                        stopRecording();
                        break;
                }
                return false;
            }
        });

        initBluetooth();
        initAudioSystem();
    }

    private void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        ArrayList<DeviceInfo> deviceList = new ArrayList<DeviceInfo>();
        if (mBluetoothAdapter != null) {
            for (BluetoothDevice device: mBluetoothAdapter.getBondedDevices())
            {
                DeviceInfo newDevice= new DeviceInfo(device.getName(),device.getAddress());
                deviceList.add(newDevice);
            }
        }

        // No devices found
        if (deviceList.size() == 0) {
            deviceList.add(new DeviceInfo("No devices found", ""));
        }

        ArrayAdapter<DeviceInfo> adapter;

        // Populate List view with device information
        adapter = new ArrayAdapter<DeviceInfo>(WalkieTalkieActivity.this, android.R.layout.simple_list_item_1, deviceList);
        ListView listView = findViewById(R.id.bluetoothClients);
        listView.setAdapter(adapter);
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

    public void stopRecording() {
        if (recorder != null) {
            isRecording = false;
            recorder.stop();
        }
    }

    public void startRecording() {
        buffer = new byte[bufferSize];

        // Start Recording
        recorder.startRecording();
        isRecording = true;
        // Start a thread
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                sendRecording();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }
    // Method for sending Audio
    public void sendRecording() {
        // Infinite loop until microphone button is released
        float[] samples = new float[bufferSize / 2];
        float lastPeak = 0f;
        while (isRecording) {
            int numberOfShort = recorder.read(buffer, 0, bufferSize);
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
                peak = lastPeak * 0.875f;
            }

            lastPeak = peak;

            energyDisplay.setProgress((int)(peak*100));
        }
        energyDisplay.setProgress(0);
    }

    @Override
    protected void onDestroy()
    {
        //Release resources for audio objects
        recorder.release();
        super.onDestroy();
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
        }
    }
}
