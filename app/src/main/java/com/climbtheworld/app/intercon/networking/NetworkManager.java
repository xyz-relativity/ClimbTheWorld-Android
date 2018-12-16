package com.climbtheworld.app.intercon.networking;

import android.app.Activity;
import android.content.Context;
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

import needle.Needle;

public class NetworkManager implements INetworkEventListener {
    public static final String MULTICAST_NETWORK_GROUP = "234.1.8.3";
    private static final int TTL = 1;
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
        int ttl = TTL;
    }

    class PingTask extends TimerTask {
        public void run() {
            discover();

            List<String> timeoutClients = new ArrayList<>();

            for (String client: connectedClients.keySet()) {
                final ClientInfo clientInfo = connectedClients.get(client);
                clientInfo.ttl-=1;
                if (clientInfo.ttl < 0) {
                    timeoutClients.add(client);
                    Needle.onMainThread().execute(new Runnable() {
                        @Override
                        public void run() {
                            wifiListView.removeView(clientInfo.view);
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
        this.udpServer = new UDPServer(1983, this);
        this.udpClient = new UDPClient(1983);
        callsign = parent.findViewById(R.id.editCallsign);
        inflater = (LayoutInflater) parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wifiListView = parent.findViewById(R.id.wifiClients);

        pingTimer = new Timer();
    }

    public void onResume() {
        udpServer.startServer();
        TimerTask pingTask = new PingTask();
        pingTimer.scheduleAtFixedRate(pingTask, 500, 5000);
//        wifiListView.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                updateClients("192.167.1.111", "PING", "testOne");
//            }
//        }, 2000);
    }

    public void onPause() {
        udpServer.stopServer();
        connectedClients.clear();
        pingTimer.cancel();
    }

    private void discover() {
        udpClient.sendData(("PING " + callsign.getText()).getBytes(), MULTICAST_NETWORK_GROUP);
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        System.out.println("Client count: " + connectedClients.size());
        System.out.println("Got Data: " + sourceAddress + " " + new String(data));
        String[] signals = (new String(data)).split(" ");
        updateClients(sourceAddress, signals[0], signals[1]);
    }

    private void updateClients(final String address, final String command, final String data) {
        switch (command) {
            case "PING":
                udpClient.sendData(("PONG " + callsign.getText()).getBytes(), address);

                Needle.onMainThread().execute(new Runnable() {
                    @Override
                    public void run() {


                        if (connectedClients.size() == 0) {
                            wifiListView.removeAllViews();
                        }

                        ClientInfo client = connectedClients.get(address);

                        if (client == null) {
                            client = new ClientInfo();
                            final View newViewElement = inflater.inflate(R.layout.list_item_walkie, wifiListView, false);
                            wifiListView.addView(newViewElement);
                            client.view = newViewElement;
                            connectedClients.put(address, client);
                        }

                        ((TextView) client.view.findViewById(R.id.deviceName)).setText(data);
                        ((TextView) client.view.findViewById(R.id.deviceAddress)).setText(address);
                    }
                });
                break;
            case "PONG":
                if (connectedClients.containsKey(address)) {
                    connectedClients.get(address).ttl = TTL;
                }
                break;
        }
    }
}
