package com.climbtheworld.app.activities;

import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpServer {

    private boolean isRunning = true;
    public static final int SERVER_PORT = 10183;
    DatagramSocket socket;

    public void startServer(TextView textView) {
        new Thread(() -> {
            try {
                socket = new DatagramSocket(SERVER_PORT);
                byte[] buffer = new byte[1024];

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // Blocks until a packet is received

                    String receivedMessage = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received: " + receivedMessage);
                    //textView.setText(receivedMessage);
                    // Process the received message
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stopServer() {
        socket.close();
        isRunning = false;
    }
}