package com.climbtheworld.app.intercom.networking.lan.backend.upd;

import static com.climbtheworld.app.utils.Constants.NETWORK_EXECUTOR;

import android.util.Log;

import com.climbtheworld.app.intercom.networking.DataFrame;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPClient {
	int remotePort;

	public UDPClient(int port) throws SocketException {
		this.remotePort = port;
	}

	public void sendData(final DataFrame sendData, final String destination) {
		NETWORK_EXECUTOR.execute(new Runnable() { //no networking on main thread
			@Override
			public void run() {
				try {
					DatagramSocket clientSocket = new DatagramSocket();
					DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), remotePort);
					clientSocket.send(sendPacket);
					clientSocket.close();
				} catch (IOException e) {
					Log.d("UDPClient", "Failed to send udp data." + e.getMessage());
				}
			}
		});
	}
}
