package com.climbtheworld.app.intercom;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

        initEditSwitcher(parent, (LinearLayout)parent.findViewById(R.id.callsignLayout), Configs.ConfigKey.callsign, EditorType.CALL_SIGN);
        initEditSwitcher(parent, (LinearLayout)parent.findViewById(R.id.channelLayout), Configs.ConfigKey.channel, EditorType.CHANNEL);
    }

    private void initEditSwitcher(final AppCompatActivity parent, final LinearLayout container, final Configs.ConfigKey configKey, final EditorType type) {

        final TextView switcherText = container.findViewById(R.id.textViewr);
        final EditText switcherEdit = container.findViewById(R.id.textEditor);
        final ImageView switcherEditDone = container.findViewById(R.id.textEditorDone);
        final ViewSwitcher switcher = container.findViewById(R.id.inputSwitcher);

        switcherText.setText(Globals.globalConfigs.getString(configKey));
        switcherEdit.setText(Globals.globalConfigs.getString(configKey));

        updateCallSign(type, switcherText.getText().toString());

        switcherText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switcher.showNext();
                switcherEdit.requestFocus();
                switcherEdit.setSelection(switcherEdit.getText().length());
                InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(switcherEdit, InputMethodManager.SHOW_FORCED);
            }
        });

        switcherEditDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switcherText.setText(switcherEdit.getText());
                Globals.globalConfigs.setString(configKey, switcherText.getText().toString());
                updateCallSign(type, switcherText.getText().toString());
                switcherEdit.clearFocus();
                InputMethodManager imm = (InputMethodManager) parent.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                switcher.showPrevious();
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