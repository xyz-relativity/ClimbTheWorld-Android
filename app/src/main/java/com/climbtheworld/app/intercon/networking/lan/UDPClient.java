package com.climbtheworld.app.intercon.networking.lan;

import android.view.View;

import com.climbtheworld.app.R;
import com.climbtheworld.app.storage.views.DataFragment;
import com.climbtheworld.app.storage.views.LocalDataFragment;
import com.climbtheworld.app.utils.Globals;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import needle.Needle;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

public class UDPClient {
    DatagramSocket clientSocket;
    int remotePort;

    public UDPClient(int port) throws SocketException {
        clientSocket = new DatagramSocket();
        remotePort = port;
    }

    public void sendData( final byte[] sendData, final String destination) {
        NETWORK_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                InetAddress target = null;
                try {
                    target = InetAddress.getByName(destination);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, target, remotePort);
                    clientSocket.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
