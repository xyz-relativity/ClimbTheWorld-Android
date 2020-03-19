package com.climbtheworld.app.intercom.networking.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.climbtheworld.app.intercom.IUiEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;
import com.climbtheworld.app.intercom.networking.INetworkBackend;
import com.climbtheworld.app.intercom.networking.INetworkFrame;
import com.climbtheworld.app.utils.Constants;

import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class LanManager implements INetworkBackend {
    private static final String MULTICAST_GROUP = "234.1.8.3";
    private static final int CTW_UDP_PORT = 10183;
    private static final int CLIENT_TIMER_COUNT = 2;
    private static final int PING_TIMER_MS = 3000;
    private final Handler handler = new Handler();
    DataFrame inFrame = new DataFrame();
    DataFrame outFrame = new DataFrame();

    private UDPServer udpServer;
    private UDPClient udpClient;
    private Timer pingTimer = new Timer();
    private String callsign;

    private Map<String, ClientInfo> connectedClients = new HashMap<>();

    private List<IUiEventListener> uiHandlers = new ArrayList<>();
    private Context parent;

    private class ClientInfo {
        String data = "";
        int ttl = CLIENT_TIMER_COUNT;
        String address = "";
        String uuid = "";
    }

    private BroadcastReceiver connectionStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(isConnected(context)) {
                initNetwork();
            }
        }

        private boolean isConnected(Context context) {
            ConnectivityManager cm =
                    (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null &&
                    activeNetwork.isConnected();
        }
    };

    public LanManager(AppCompatActivity parent) throws SocketException {
        this.udpServer = new UDPServer(CTW_UDP_PORT, MULTICAST_GROUP);
        udpServer.addListener(new INetworkEventListener() {
            @Override
            public void onDataReceived(String sourceAddress, byte[] data) {
                inFrame.fromTransport(data);

                if (inFrame.getFrameType() == INetworkFrame.FrameType.DATA) {
                    if (connectedClients.containsKey(sourceAddress)) {
                        for (IUiEventListener uiHandler: uiHandlers) {
                            uiHandler.onData(inFrame.getData());
                        }
                    }
                } else if (inFrame.getFrameType() == INetworkFrame.FrameType.SIGNAL) {
                    String[] signals = (new String(inFrame.getData())).split(" ");
                    updateClients(sourceAddress, signals[0], signals[1], signals[2]);
                };
            }
        });
        this.udpClient = new UDPClient(CTW_UDP_PORT);

        this.parent = parent;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        parent.registerReceiver(connectionStatus, intentFilter);
    }

    public void updateCallSign(String callSign) {
        this.callsign = callSign;
    }

    public void addListener(IUiEventListener listener) {
        uiHandlers.add(listener);
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
                uiHandler.onClientConnected(IUiEventListener.ClientType.LAN, address, data);
            }
        }

        if (client.data.compareTo(data) != 0) {
            for (IUiEventListener uiHandler: uiHandlers) {
                uiHandler.onClientUpdated(IUiEventListener.ClientType.LAN, address,data);
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
                        uiHandler.onClientDisconnected(IUiEventListener.ClientType.LAN, clientInfo.address, clientInfo.data);
                    }
                }
            }

            for (String client: timeoutClients) {
                connectedClients.remove(client);
            }
        }
    }

    private void discover() {
        doPing(MULTICAST_GROUP);
    }

    private void doPing(String address) {
        outFrame.fromData(("PING " + Constants.myUUID + " " + callsign).getBytes(), INetworkFrame.FrameType.SIGNAL);
        udpClient.sendData(outFrame, address);
    }

    private void doPong(String address) {
        outFrame.fromData(("PONG " + Constants.myUUID + " " + callsign).getBytes(), INetworkFrame.FrameType.SIGNAL);
        udpClient.sendData(outFrame, address);
    }

    public void onStart() {
    }

    @Override
    public void onResume() {

    }

    public void onDestroy() {
        closeNetwork();
    }

    @Override
    public void onPause() {

    }

    private void initNetwork() {
        udpServer.startServer();
        TimerTask pingTask = new PingTask();
        pingTimer.scheduleAtFixedRate(pingTask, 0, PING_TIMER_MS);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                discover();
            }
        }, 500);
    }

    private void closeNetwork() {
        udpServer.stopServer();
        pingTimer.cancel();
        parent.unregisterReceiver(connectionStatus);
    }

    public void sendData(byte[] frame, int numberOfReadBytes) {
        outFrame.fromData(frame, INetworkFrame.FrameType.DATA);
        for (ClientInfo client: connectedClients.values()) {
            udpClient.sendData(outFrame, client.address);
        }
    }
}