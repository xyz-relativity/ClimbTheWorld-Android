package com.climbtheworld.app.intercon.networking.lan;

import com.climbtheworld.app.intercon.networking.INetworkEventListener;
import com.climbtheworld.app.intercon.networking.NetworkManager;
import com.climbtheworld.app.intercon.audiotools.RecordingThread;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

public class UDPServer {
    private Integer serverPort;
    private String bindGroup;
    private ServerThread server;
    private List<INetworkEventListener> listeners = new ArrayList<>();

    class ServerThread extends Thread {

        boolean isRunning = true;

        @Override
        public void run() {
            try {
                MulticastSocket serverSocket = new MulticastSocket(serverPort);

                InetAddress group = null;
                if (bindGroup != null && !bindGroup.isEmpty()) {
                    group = InetAddress.getByName(bindGroup);
                    serverSocket.joinGroup(group);
                }

                byte[] receiveData = new byte[RecordingThread.BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                while(isRunning) {
                    serverSocket.receive(receivePacket);

                    InetAddress ipAddress = receivePacket.getAddress();
                    int port = receivePacket.getPort();

                    byte[] result = new byte[receivePacket.getLength()];
                    System.arraycopy(receivePacket.getData(), 0, result, 0, receivePacket.getLength());
                    notifyListeners(ipAddress.getHostAddress(), result);
                }

                if (group != null) {
                    serverSocket.leaveGroup(group);
                }
                serverSocket.close();

            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }

        private void notifyListeners(String address, byte[] data) {
            for (INetworkEventListener obs: listeners) {
                obs.onDataReceived(address, data);
            }
        }

        void stopServer() {
            isRunning = false;
        }
    }

    public UDPServer(int port, String group) {
        this.serverPort = port;
        this.bindGroup = group;
    }

    public void addListener(INetworkEventListener listener) {
        this.listeners.add(listener);
    }

    public void startServer() {
        if (server != null) {
            stopServer();
        }

        server = new ServerThread();
        server.start();
    }

    public void stopServer() {
        server.stopServer();
        server = null;
    }
}
