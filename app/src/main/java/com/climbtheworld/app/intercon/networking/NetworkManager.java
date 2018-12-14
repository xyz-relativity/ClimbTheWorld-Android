package com.climbtheworld.app.intercon.networking;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.climbtheworld.app.R;
import com.climbtheworld.app.intercon.networking.lan.UDPClient;
import com.climbtheworld.app.intercon.networking.lan.UDPServer;

import java.io.IOException;
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

        parent.findViewById(R.id.editCallsign);

        callsign = parent.findViewById(R.id.editCallsign);
        callsign.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId,
                                          KeyEvent keyEvent) { //triggered when done editing (as clicked done on keyboard)
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    textView.clearFocus();
                    if (textView.length() == 0) {
                        textView.setText("Unknown");
                    }

                    discover();
                }
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
