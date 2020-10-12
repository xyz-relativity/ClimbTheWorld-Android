package com.climbtheworld.app.intercom.networking.wifi;

import com.climbtheworld.app.intercom.networking.INetworkFrame;

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

	public void sendData(final INetworkFrame sendData, final String destination) {
		NETWORK_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				InetAddress target;
				try {
					target = InetAddress.getByName(destination);
					DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.getLength(), target, remotePort);
					clientSocket.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
}
