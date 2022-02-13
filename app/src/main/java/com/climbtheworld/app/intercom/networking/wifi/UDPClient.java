package com.climbtheworld.app.intercom.networking.wifi;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

import android.util.Log;

import com.climbtheworld.app.intercom.networking.DataFrame;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPClient {
	DatagramSocket clientSocket;
	int remotePort;

	public UDPClient(int port) throws SocketException {
		clientSocket = new DatagramSocket();
		remotePort = port;
	}

	public void sendData(final DataFrame sendData, final String destination) {
		NETWORK_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				try {
					DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), remotePort);
					clientSocket.send(sendPacket);
				} catch (IOException e) {
					Log.d("====== UDP", "Failed to send udp data.", e);
				}
			}
		});
	}
}
