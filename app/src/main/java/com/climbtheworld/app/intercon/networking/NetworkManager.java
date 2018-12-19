package com.climbtheworld.app.intercon.networking;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.audiotools.IRecordingListener;
import com.climbtheworld.app.intercon.audiotools.PlaybackThread;
import com.climbtheworld.app.intercon.networking.bluetooth.BluetoothServer;
import com.climbtheworld.app.intercon.networking.lan.UDPClient;
import com.climbtheworld.app.intercon.networking.lan.UDPServer;
import com.climbtheworld.app.utils.Constants;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import needle.Needle;

public class NetworkManager implements INetworkEventListener, IRecordingListener {
    private static final String MULTICAST_SIGNALING_NETWORK_GROUP = "234.1.8.3";
    private static final String MULTICAST_DATA_NETWORK_GROUP = "234.1.8.4";
    private static final int SIGNALING_PORT = 10183;
    private static final int DATA_PORT = 10184;
    private static final int CLIENT_TIMER_COUNT = 2;
    private static final int PING_TIMER_MS = 3000;
    private final Handler handler = new Handler();

    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private PlaybackThread playbackThread;

    private Activity parent;
    private UDPServer udpServer;
    private UDPClient udpClient;
    private UDPServer udpDataServer;
    private UDPClient udpDataClient;
    private ClientsContainer wifiListView;
    private ClientsContainer bluetoothListView;
    private Timer pingTimer;

    private BluetoothServer bluetoothServer;

    private LayoutInflater inflater;
    private EditText callsign;

    private Map<String, ClientInfo> connectedClients = new HashMap<>();

    private class ClientInfo {
        View view;
        int ttl = CLIENT_TIMER_COUNT;
        String address;
        String uuid;
    }

    private class ClientsContainer {
        ViewGroup listView;
        ViewGroup emptyListView;

    }

    class PingTask extends TimerTask {
        public void run() {
            List<String> timeoutClients = new ArrayList<>();

            for (String client: connectedClients.keySet()) {
                final ClientInfo clientInfo = connectedClients.get(client);
                clientInfo.ttl-=1;
                if (clientInfo.ttl == 0) {
                    doPing(clientInfo.address);
                }
                if (clientInfo.ttl < 0) {
                    timeoutClients.add(client);
                    Needle.onMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            wifiListView.listView.removeView(clientInfo.view);

                            updateEmpty(wifiListView);
                        }
                    });
                }
            }

            for (String client: timeoutClients) {
                connectedClients.remove(client);
            }
        }
    }

    public NetworkManager(final Activity parent) throws SocketException {
        this.parent = parent;
        this.udpServer = new UDPServer(SIGNALING_PORT, MULTICAST_SIGNALING_NETWORK_GROUP);
        udpServer.addListener(this);
        this.udpClient = new UDPClient(SIGNALING_PORT);

        this.bluetoothServer = new BluetoothServer(Constants.myUUID);

        playbackThread = new PlaybackThread(queue);

        Constants.AUDIO_PLAYER_EXECUTOR.execute(playbackThread);

        this.udpDataServer = new UDPServer(DATA_PORT, MULTICAST_DATA_NETWORK_GROUP);
        udpDataServer.addListener(new INetworkEventListener() {
            @Override
            public void onDataReceived(String sourceAddress, byte[] data) {
                if (connectedClients.containsKey(sourceAddress)) {
                    queue.offer(data);
                }
            }
        });
        this.udpDataClient = new UDPClient(DATA_PORT);

        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wifiListView = new ClientsContainer();
        wifiListView.listView = parent.findViewById(R.id.wifiClients);
        wifiListView.emptyListView = parent.findViewById(R.id.wifiClientsMessage);

        bluetoothListView = new ClientsContainer();
        bluetoothListView.listView = parent.findViewById(R.id.bluetoothClients);
        bluetoothListView.emptyListView = parent.findViewById(R.id.bluetoothClientsMessage);
    }

    public  void onStart() {
        udpServer.startServer();
        udpDataServer.startServer();
        TimerTask pingTask = new PingTask();
        pingTimer = new Timer();
        pingTimer.scheduleAtFixedRate(pingTask, 0, PING_TIMER_MS);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                discover();
            }
        }, 1000);
    }

    public void onResume() {
    }

    public void onDestroy() {
        udpServer.stopServer();
        udpDataServer.stopServer();
        pingTimer.cancel();
        playbackThread.stopPlayback();
    }

    public void onPause() {
    }

    @Override
    public void onRecordingStarted() {

    }

    @Override
    public void onRawAudio(byte[] frame, int numberOfReadBytes) {
        udpDataClient.sendData(frame, numberOfReadBytes, MULTICAST_DATA_NETWORK_GROUP);
    }

    @Override
    public void onAudio(final byte[] frame, int numberOfReadBytes, double energy, double rms) {

    }

    @Override
    public void onRecordingDone() {

    }

    private void discover() {
        doPing(MULTICAST_SIGNALING_NETWORK_GROUP);
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        String[] signals = (new String(data)).split(" ");
        updateClients(sourceAddress, signals[0], signals[1], signals[2]);
    }

    private void doPing(String address) {
        udpClient.sendData(("PING " + Constants.myUUID + " " + callsign.getText()).getBytes(), address);
    }

    private void doPong(String address) {
        udpClient.sendData(("PONG " + Constants.myUUID + " " + callsign.getText()).getBytes(), address);
    }

    private void updateClients(final String address, final String command, final String uuid, final String data) {
        if (Constants.myUUID.compareTo(UUID.fromString(uuid)) == 0) {
            return;
        }

        if (command.equals("PING")) {
            doPong(address);
        }

        if (connectedClients.containsKey(address)) {
            connectedClients.get(address).ttl = CLIENT_TIMER_COUNT;
        }

        Needle.onMainThread().execute(new Runnable() {
            @Override
            public void run() {
                ClientInfo client = connectedClients.get(address);

                if (client == null) {
                    client = new ClientInfo();
                    client.view = updateClients(wifiListView, data);
                    client.uuid = uuid;
                    client.address = address;
                    connectedClients.put(address, client);
                }

                ((TextView) client.view.findViewById(R.id.deviceName)).setText(data);
                ((TextView) client.view.findViewById(R.id.deviceAddress)).setText(address);
            }
        });
    }

    private View updateClients(ClientsContainer container, String data) {
        final View newViewElement = inflater.inflate(R.layout.list_item_walkie, container.listView, false);

        int i;
        for (i = 0; i < container.listView.getChildCount(); ++i) {
            if (((TextView)container.listView.getChildAt(i).findViewById(R.id.deviceName)).getText().toString().compareTo(data) >= 0) {
                break;
            }
        }
        container.listView.addView(newViewElement, i);
        updateEmpty(container);

        return newViewElement;
    }

    private void updateEmpty(ClientsContainer container) {
        if (container.listView.getChildCount() > 0) {
            parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.GONE);
        } else {
            parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.VISIBLE);
        }
    }
}
