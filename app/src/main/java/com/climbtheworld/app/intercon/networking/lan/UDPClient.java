package com.climbtheworld.app.intercon.networking.lan;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

public class UDPClient {
    DatagramSocket clientSocket;
    int remotePort;

    public UDPClient(int port) throws SocketException {
        clientSocket = new DatagramSocket();
        remotePort = port;
    }

    public void sendData( final byte[] sendData, final String destination) {
        sendData(sendData, sendData.length, destination);
    }

    public void sendData( final byte[] sendData, final int numBytes,  final String destination) {
        NETWORK_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                InetAddress target;
                try {
                    target = InetAddress.getByName(destination);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, numBytes, target, remotePort);
                    clientSocket.send(sendPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
