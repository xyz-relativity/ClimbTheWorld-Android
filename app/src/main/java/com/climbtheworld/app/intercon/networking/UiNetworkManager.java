package com.climbtheworld.app.intercon.networking;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.audiotools.IRecordingListener;
import com.climbtheworld.app.intercon.audiotools.PlaybackThread;
import com.climbtheworld.app.intercon.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercon.networking.lan.LanManager;
import com.climbtheworld.app.utils.Constants;

import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class UiNetworkManager implements IUiEventListener, IRecordingListener {
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private PlaybackThread playbackThread;

    private Activity parent;

    private ClientsContainer wifiListView;
    private ClientsContainer bluetoothListView;

    private LayoutInflater inflater;
    private EditText callsign;
    private LanManager lanManager;
    private BluetoothManager bluetoothManager;

    @Override
    public void onData(byte[] data) {
        queue.offer(data);
    }

    @Override
    public void onClientConnected(ClientType type, String address, String data) {
        switch (type) {
            case LAN:
                addClients(wifiListView, address, data);
                break;
            case BLUETOOTH:
                addClients(bluetoothListView, address, data);
                break;
        }
    }

    @Override
    public void onClientUpdated(ClientType type, String address, String data) {
        switch (type) {
            case LAN:
                updateClients(wifiListView, address, data);
                break;
            case BLUETOOTH:
                updateClients(bluetoothListView, address, data);
                break;
        }
    }

    @Override
    public void onClientDisconnected(ClientType type, String address, String data) {
        switch (type) {
            case LAN:
                removeClients(wifiListView, address, data);
                break;
            case BLUETOOTH:
                removeClients(bluetoothListView, address, data);
                break;
        }
    }

    private class ClientsContainer {
        ViewGroup listView;
        ViewGroup emptyListView;
    }

    public UiNetworkManager(final Activity parent) throws SocketException {
        this.parent = parent;
        playbackThread = new PlaybackThread(queue);
        Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wifiListView = new ClientsContainer();
        wifiListView.listView = parent.findViewById(R.id.wifiClients);
        wifiListView.emptyListView = parent.findViewById(R.id.wifiClientsMessage);

        bluetoothListView = new ClientsContainer();
        bluetoothListView.listView = parent.findViewById(R.id.bluetoothClients);
        bluetoothListView.emptyListView = parent.findViewById(R.id.bluetoothClientsMessage);

        lanManager = new LanManager();
        lanManager.addListener(this);
        bluetoothManager = new BluetoothManager();
        bluetoothManager.addListener(this);
    }

    public void onStart() {
        lanManager.onStart();
        bluetoothManager.onStart();
    }

    public void onResume() {
        lanManager.updateCallsign(callsign.getText().toString());
        bluetoothManager.updateCallsign(callsign.getText().toString());
    }

    public void onDestroy() {
        lanManager.onDestroy();
        bluetoothManager.onDestroy();
        playbackThread.stopPlayback();
    }

    public void onPause() {
    }

    @Override
    public void onRecordingStarted() {

    }

    @Override
    public void onRawAudio(byte[] frame, int numberOfReadBytes) {
        lanManager.sendData(frame, numberOfReadBytes);
        bluetoothManager.sendData(frame, numberOfReadBytes);
    }

    @Override
    public void onAudio(final byte[] frame, int numberOfReadBytes, double energy, double rms) {

    }

    @Override
    public void onRecordingDone() {

    }

    private void addClients(final ClientsContainer container,final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.deviceName)).getText().toString().compareTo(data) >= 0) {
                        break;
                    }
                }

                final View newViewElement = inflater.inflate(R.layout.list_item_walkie, container.listView, false);
                ((TextView) newViewElement.findViewById(R.id.deviceName)).setText(data);
                ((TextView) newViewElement.findViewById(R.id.deviceAddress)).setText(address);
                container.listView.addView(newViewElement, i);
                updateEmpty(container);
            }
        });
    }

    private void updateClients(final ClientsContainer container,final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.deviceAddress)).getText().toString().compareTo(address) == 0) {
                        break;
                    }
                }

                ((TextView) container.listView.getChildAt(i).findViewById(R.id.deviceName)).setText(data);
                ((TextView) container.listView.getChildAt(i).findViewById(R.id.deviceAddress)).setText(address);
            }
        });
    }

    private void removeClients(final ClientsContainer container,final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.deviceAddress)).getText().toString().compareTo(address) == 0) {
                        container.listView.removeViewAt(i);
                        break;
                    }
                }
                updateEmpty(container);
            }
        });
    }

    private void updateEmpty(ClientsContainer container) {
        if (container.listView.getChildCount() > 0) {
            container.emptyListView.setVisibility(View.GONE);
        } else {
            container.emptyListView.setVisibility(View.VISIBLE);
        }
    }
}
