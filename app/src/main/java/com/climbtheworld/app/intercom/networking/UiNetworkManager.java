package com.climbtheworld.app.intercom.networking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercom.audiotools.IRecordingListener;
import com.climbtheworld.app.intercom.audiotools.PlaybackThread;
import com.climbtheworld.app.intercom.networking.bluetooth.BluetoothManager;
import com.climbtheworld.app.intercom.networking.p2pwifi.P2PWiFiManager;
import com.climbtheworld.app.intercom.networking.wifi.LanManager;
import com.climbtheworld.app.utils.Configs;
import com.climbtheworld.app.utils.Constants;
import com.climbtheworld.app.utils.Globals;

import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class UiNetworkManager implements IUiEventListener, IRecordingListener {
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final ListView channelListView;
    private PlaybackThread playbackThread;

    private LayoutInflater inflater;
    private LanManager lanManager;
    private BluetoothManager bluetoothManager;
    private P2PWiFiManager p2pWifiManager;
    List<Client> clients = new LinkedList<>();

    private class Client {
        public Client(ClientType type, String name, String address) {
            this.Name = name;
            this.address = address;
        }
        String address;
        String Name;
        ClientType type;
    }

    private enum EditorType {
        CALL_SIGN,
        CHANNEL;
    }

    private BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return clients.size();
        }

        @Override
        public Object getItem(int position) {
            return clients.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_item_with_description, channelListView, false);
            }
            ((TextView) convertView.findViewById(R.id.itemTitle)).setText(clients.get(position).Name);
            ((TextView) convertView.findViewById(R.id.itemDescription)).setText(clients.get(position).address);
            return convertView;
        }
    };

    public UiNetworkManager(final AppCompatActivity parent) throws SocketException {
        playbackThread = new PlaybackThread(queue);
        Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        channelListView = parent.findViewById(R.id.listChannel);
        channelListView.setAdapter(adapter);

        lanManager = new LanManager(parent);
        lanManager.addListener(this);

        bluetoothManager = new BluetoothManager();
        bluetoothManager.addListener(this);

        p2pWifiManager = new P2PWiFiManager(parent);
        p2pWifiManager.addListener(this);

        initEditSwitcher(parent, (ViewSwitcher)parent.findViewById(R.id.callsignSwitcher), Configs.ConfigKey.callsign, EditorType.CALL_SIGN);
        initEditSwitcher(parent, (ViewSwitcher)parent.findViewById(R.id.channelSwitcher), Configs.ConfigKey.channel, EditorType.CHANNEL);
    }

    private void initEditSwitcher(final AppCompatActivity parent, final ViewSwitcher switcher, final Configs.ConfigKey configKey, final EditorType type) {

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

        updateCallSign(type, finalSwitchertext.getText().toString());

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
                    updateCallSign(type, finalSwitchertext.getText().toString());
                    switcher.showNext();
                }
            }
        });

    }

    private void updateCallSign(EditorType type, String callSign) {
        switch (type) {
            case CALL_SIGN:
                lanManager.updateCallSign(callSign);
                bluetoothManager.updateCallSign(callSign);
                p2pWifiManager.updateCallSign(callSign);
                break;
            case CHANNEL:
                p2pWifiManager.updateChannel(callSign);
                break;
        }
    }

    @Override
    public void onData(byte[] data) {
        queue.offer(data);
    }

    @Override
    public void onClientConnected(ClientType type, String address, String name) {
//        switch (type) {
//            case LAN:
//                addClients(wifiListView, address, data);
//                break;
//            case BLUETOOTH:
//                addClients(bluetoothListView, address, data);
//                break;
//        }
        clients.add(new Client(type, name, address));
        notifyChange();
    }

    @Override
    public void onClientUpdated(ClientType type, String address, String name) {
        for (Client client: clients) {
            if (client.address.equalsIgnoreCase(address)) {
                client.address = address;
                client.Name = name;
                break;
            }
        }
        notifyChange();
    }

    @Override
    public void onClientDisconnected(ClientType type, String address, String name) {
        for (Client client: clients) {
            if (client.address.equalsIgnoreCase(address)) {
                clients.remove(client);
                break;
            }
        }
        notifyChange();
    }

    public void onStart() {
        lanManager.onStart();
        bluetoothManager.onStart();
        p2pWifiManager.onStart();
    }

    public void onResume() {
        lanManager.onResume();
        bluetoothManager.onResume();
        p2pWifiManager.onResume();
    }

    public void onDestroy() {
        playbackThread.stopPlayback();

        lanManager.onDestroy();
        bluetoothManager.onDestroy();
        p2pWifiManager.onDestroy();
    }

    public void onPause() {
        lanManager.onPause();
        bluetoothManager.onPause();
        p2pWifiManager.onPause();
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

    private void notifyChange() {
        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }
}
