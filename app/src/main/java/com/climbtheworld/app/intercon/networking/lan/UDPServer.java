package com.climbtheworld.app.intercon.networking.lan;

import com.climbtheworld.app.intercon.networking.INetworkEventListener;
import com.climbtheworld.app.intercon.networking.NetworkManager;
import com.climbtheworld.app.intercon.voicetools.RecordingThread;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

public class UDPServer {
    private Integer serverPort;
    private ServerThread server;
    private List<INetworkEventListener> listeners = new ArrayList<>();

    class ServerThread extends Thread {

        boolean isRunning = true;

        @Override
        public void run() {
            try {
                MulticastSocket serverSocket = new MulticastSocket(serverPort);
                InetAddress group = InetAddress.getByName(NetworkManager.MULTICAST_NETWORK_GROUP);
                serverSocket.joinGroup(group);

                byte[] receiveData = new byte[RecordingThread.BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                while(isRunning) {
                    serverSocket.receive(receivePacket);

                    InetAddress ipAddress = receivePacket.getAddress();
                    int port = receivePacket.getPort();

                    byte[] result = new byte[receivePacket.getLength()];
                    System.arraycopy(receivePacket.getData(), 0, result, 0, receivePacket.getLength());
                    notifyListeners(ipAddress.toString() + ":" + port, result);
                }

                serverSocket.leaveGroup(group);
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

    public UDPServer(int port, INetworkEventListener listener) {
        this.serverPort = port;
        this.listeners.add(listener);
    }

    public void startServer() {
        if (server != null) {
            stopServer();
            try {
                server.join();
            } catch (InterruptedException ignored) {
            }
        }

        server = new ServerThread();
        server.start();
    }

    public void stopServer() {
        server.stopServer();
    }
}
