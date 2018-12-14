package com.climbtheworld.app.intercon.networking;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.networking.lan.UDPClient;
import com.climbtheworld.app.intercon.networking.lan.UDPServer;

import java.net.SocketException;

public class NetworkManager implements INetworkEventListener {
    public static final String MULTICAST_NETWORK_GROUP = "228.5.6.7";
    private Activity parent;
    private UDPServer udpServer;
    private UDPClient udpClient;
    EditText callsign;

    public NetworkManager(final Activity parent) throws SocketException {
        this.parent = parent;
        this.udpServer = new UDPServer(1983, this);
        this.udpClient = new UDPClient(1983);
        callsign = parent.findViewById(R.id.editCallsign);

        parent.findViewById(R.id.mainContainer).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    callsign.setFocusable(false);
                    callsign.setFocusableInTouchMode(false);
                }

                return false;
            }
        });
        callsign.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                v.setFocusable(true);
                v.setFocusableInTouchMode(true);
                return false;
            }
        });
    }

    public void onResume() {
        udpServer.startServer();
        discover();
    }

    public void onPause() {
        udpServer.stopServer();
    }

    private void discover() {
        udpClient.sendData(("PING " + callsign.getText()).getBytes(), MULTICAST_NETWORK_GROUP);
    }

    @Override
    public void onDataReceived(String sourceAddress, byte[] data) {
        System.out.println("--------Got data: " + sourceAddress + " " + new String(data));
    }
}
