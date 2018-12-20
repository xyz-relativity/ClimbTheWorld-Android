package com.climbtheworld.app.intercon.networking.lan;

import android.os.Handler;
import com.climbtheworld.app.intercon.networking.IUiEventListener;
import com.climbtheworld.app.utils.Constants;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class LanManager implements INetworkEventListener {
    private static final String MULTICAST_SIGNALING_NETWORK_GROUP = "234.1.8.3";
    private static final String MULTICAST_DATA_NETWORK_GROUP = "234.1.8.4";
    private static final int SIGNALING_PORT = 10183;
    private static final int DATA_PORT = 10184;
    private static final int CLIENT_TIMER_COUNT = 2;
    private static final int PING_TIMER_MS = 3000;
    private final Handler handler = new Handler();

    private UDPServer udpServer;
    private UDPClient udpClient;
    private UDPServer udpDataServer;
    private UDPClient udpDataClient;
    private Timer pingTimer;
    private String callsign;

    private Map<String, ClientInfo> connectedClients = new HashMap<>();

    private List<IUiEventListener> uiHandlers = new ArrayList<>();

    private class ClientInfo {
        String data = "";
        int ttl = CLIENT_TIMER_COUNT;
        String address = "";
        String uuid = "";
    }

    public LanManager() throws SocketException {
        this.udpServer = new UDPServer(SIGNALING_PORT, MULTICAST_SIGNALING_NETWORK_GROUP);
        udpServer.addListener(this);
        this.udpClient = new UDPClient(SIGNALING_PORT);

        this.udpDataServer = new UDPServer(DATA_PORT);
        udpDataServer.addListener(new INetworkEventListener() {
            @Override
            public void onDataReceived(String sourceAddress, byte[] data) {
                if (connectedClients.containsKey(sourceAddress)) {
                    for (IUiEventListener uiHandler: uiHandlers) {
                        uiHandler.onData(data);
                    }
                }
            }
        });
        this.udpDataClient = new UDPClient(DATA_PORT);
    }

    public void updateCallsign(String callsign) {
        this.callsign = callsign;
    }

    public void addListener(IUiEventListener listener) {
        uiHandlers.add(listener);
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        String[] signals = (new String(data)).split(" ");
        updateClients(sourceAddress, signals[0], signals[1], signals[2]);
    }

    private void updateClients(final String address, final String command, final String uuid, final String data) {
        if (Constants.myUUID.compareTo(UUID.fromString(uuid)) == 0) {
            return;
        }

        if (command.equals("PING")) {
            doPong(address);
        }

        ClientInfo client = connectedClients.get(address);

        if (client == null) {
            client = new ClientInfo();
            connectedClients.put(address, client);

            for (IUiEventListener uiHandler: uiHandlers) {
                uiHandler.onClientConnected(address, uuid, data);
            }
        }

        if (client.data.compareTo(data) != 0) {
            for (IUiEventListener uiHandler: uiHandlers) {
                uiHandler.onClientUpdated(address, uuid, data);
            }
        }

        client.ttl = CLIENT_TIMER_COUNT;
        client.uuid = uuid;
        client.address = address;
        client.data = data;
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

                    for (IUiEventListener uiHandler: uiHandlers) {
                        uiHandler.onClientDisconnected(clientInfo.address, clientInfo.uuid, clientInfo.data);
                    }
                }
            }

            for (String client: timeoutClients) {
                connectedClients.remove(client);
            }
        }
    }

    private void discover() {
        doPing(MULTICAST_SIGNALING_NETWORK_GROUP);
    }

    private void doPing(String address) {
        udpClient.sendData(("PING " + Constants.myUUID + " " + callsign).getBytes(), address);
    }

    private void doPong(String address) {
        udpClient.sendData(("PONG " + Constants.myUUID + " " + callsign).getBytes(), address);
    }

    public void onStart() {
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

    public void onDestroy() {
        udpServer.stopServer();
        udpDataServer.stopServer();
        pingTimer.cancel();
    }

    public void sendData(byte[] frame, int numberOfReadBytes) {
        for (ClientInfo client: connectedClients.values()) {
            udpDataClient.sendData(frame, numberOfReadBytes, client.address);
        }
    }
}
