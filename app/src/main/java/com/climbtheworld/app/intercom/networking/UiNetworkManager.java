package com.climbtheworld.app.intercom.networking;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.lan.LanManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class UiNetworkManager implements IUiEventListener, IRecordingListener {
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private PlaybackThread playbackThread;

    private ClientsContainer channelListView;

    private LayoutInflater inflater;
    private EditText callsign;
    private LanManager lanManager;
    private BluetoothManager bluetoothManager;

    public UiNetworkManager(final AppCompatActivity parent) throws SocketException {
        playbackThread = new PlaybackThread(queue);
        Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        channelListView = new ClientsContainer();
        channelListView.listView = parent.findViewById(R.id.listChannel);

        initEditSwitcher(parent, (ViewSwitcher)parent.findViewById(R.id.callsignSwitcher), Configs.ConfigKey.callsign,null);
        initEditSwitcher(parent, (ViewSwitcher)parent.findViewById(R.id.channelSwitcher), Configs.ConfigKey.channel,null);

        lanManager = new LanManager(parent);
        lanManager.addListener(this);

        bluetoothManager = new BluetoothManager();
        bluetoothManager.addListener(this);
    }

    private void initEditSwitcher(final AppCompatActivity parent, final ViewSwitcher switcher, final Configs.ConfigKey configKey, Object listener) {

        TextView switcherText = null;
        EditText switcherEdit = null;

        int count = switcher.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = switcher.getChildAt(i);
            if (v instanceof EditText) {
                switcherEdit = (EditText)v;
            } else if (v instanceof TextView) {
                switcherText = (TextView)v;
            }
        }

        switcherText.setText(Globals.globalConfigs.getString(configKey));
        switcherEdit.setText(Globals.globalConfigs.getString(configKey));

        final EditText finalSwitcherEdit = switcherEdit;
        final TextView finalSwitchertext = switcherText;

        finalSwitchertext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switcher.showNext();
                finalSwitcherEdit.requestFocus();
                finalSwitcherEdit.setSelection(finalSwitcherEdit.getText().length());
                InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(finalSwitcherEdit, InputMethodManager.SHOW_FORCED);
            }
        });

        finalSwitcherEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                if (!b) {
                    finalSwitchertext.setText(finalSwitcherEdit.getText());
                    Globals.globalConfigs.setString(configKey, finalSwitchertext.getText().toString());
                    switcher.showNext();
                }
            }
        });

    }

    @Override
    public void onData(byte[] data) {
        queue.offer(data);
    }

    @Override
    public void onClientConnected(ClientType type, String address, String data) {
//        switch (type) {
//            case LAN:
//                addClients(wifiListView, address, data);
//                break;
//            case BLUETOOTH:
//                addClients(bluetoothListView, address, data);
//                break;
//        }
        addClients(channelListView, address, data);
    }

    @Override
    public void onClientUpdated(ClientType type, String address, String data) {
        updateClients(channelListView, address, data);
    }

    @Override
    public void onClientDisconnected(ClientType type, String address, String data) {
        removeClients(channelListView, address, data);
    }

    private class ClientsContainer {
        ViewGroup listView;
        ViewGroup emptyListView;
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
        playbackThread.stopPlayback();
        bluetoothManager.onDestroy();
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

    private void addClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemTitle)).getText().toString().compareTo(data) >= 0) {
                        break;
                    }
                }

                final View newViewElement = inflater.inflate(R.layout.list_item_with_description, container.listView, false);
                ((TextView) newViewElement.findViewById(R.id.itemTitle)).setText(data);
                ((TextView) newViewElement.findViewById(R.id.itemDescription)).setText(address);
                container.listView.addView(newViewElement, i);
                updateEmpty(container);
            }
        });
    }

    private void updateClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).getText().toString().compareTo(address) == 0) {
                        break;
                    }
                }

                ((TextView) container.listView.getChildAt(i).findViewById(R.id.itemTitle)).setText(data);
                ((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).setText(address);
            }
        });
    }

    private void removeClients(final ClientsContainer container, final String address, final String data) {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                int i;
                for (i = 0; i < container.listView.getChildCount(); ++i) {
                    if (((TextView) container.listView.getChildAt(i).findViewById(R.id.itemDescription)).getText().toString().compareTo(address) == 0) {
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
