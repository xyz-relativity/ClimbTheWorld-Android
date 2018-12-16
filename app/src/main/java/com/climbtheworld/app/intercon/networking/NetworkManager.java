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
import com.climbtheworld.app.intercon.networking.lan.UDPClient;
import com.climbtheworld.app.intercon.networking.lan.UDPServer;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import needle.Needle;

public class NetworkManager implements INetworkEventListener {
    public static final String MULTICAST_SIGNALING_NETWORK_GROUP = "234.1.8.3";
    public static final String MULTICAST_DATA_NETWORK_GROUP = "234.1.8.4";
    private static final int SIGNALING_PORT = 10183;
    private static final int DATA_PORT = 10184;
    private static final int CLIENT_TIMER_COUNT = 2;
    private static final int PING_TIMER_MS = 3000;
    final Handler handler = new Handler();

    private UUID clientUUID = UUID.randomUUID();
    private Activity parent;
    private UDPServer udpServer;
    private UDPClient udpClient;
    private ViewGroup wifiListView;
    private Timer pingTimer;

    private LayoutInflater inflater;
    EditText callsign;

    private Map<String, ClientInfo> connectedClients = new HashMap<>();

    private class ClientInfo {
        View view;
        int ttl = CLIENT_TIMER_COUNT;
        String address;
        String uuid;
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
                            wifiListView.removeView(clientInfo.view);

                            if (wifiListView.getChildCount() > 0) {
                                parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.GONE);
                            } else {
                                parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.VISIBLE);
                            }
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
        this.udpServer = new UDPServer(SIGNALING_PORT, this);
        this.udpClient = new UDPClient(SIGNALING_PORT);
        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wifiListView = parent.findViewById(R.id.wifiClients);
    }

    public void onResume() {
        udpServer.startServer();
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

    public void onPause() {
        udpServer.stopServer();
        connectedClients.clear();
        pingTimer.cancel();
    }

    private void discover() {
        doPing(MULTICAST_SIGNALING_NETWORK_GROUP);
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        System.out.println("Client count: " + connectedClients.size());
        System.out.println("Got Data: " + sourceAddress + " " + new String(data));
        String[] signals = (new String(data)).split(" ");
        updateClients(sourceAddress, signals[0], signals[1], signals[2]);
    }

    private void doPing(String address) {
        udpClient.sendData(("PING " + clientUUID + " " + callsign.getText()).getBytes(), address);
    }

    private void doPong(String address) {
        udpClient.sendData(("PONG " + clientUUID + " " + callsign.getText()).getBytes(), address);
    }

    private void updateClients(final String address, final String command, final String uuid, final String data) {
        if (clientUUID.compareTo(UUID.fromString(uuid)) == 0) {
//            return;
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
                    final View newViewElement = inflater.inflate(R.layout.list_item_walkie, wifiListView, false);
                    wifiListView.addView(newViewElement);

                    client.view = newViewElement;
                    client.uuid = uuid;
                    client.address = address;
                    connectedClients.put(address, client);
                }

                ((TextView) client.view.findViewById(R.id.deviceName)).setText(data);
                ((TextView) client.view.findViewById(R.id.deviceAddress)).setText(address);

                if (wifiListView.getChildCount() > 0) {
                    parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.GONE);
                } else {
                    parent.findViewById(R.id.wifiClientsMessage).setVisibility(View.VISIBLE);
                }
            }
        });
    }
}
