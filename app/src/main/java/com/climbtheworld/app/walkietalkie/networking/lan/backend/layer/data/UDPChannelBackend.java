package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.data;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class UDPChannelBackend extends Thread {
	public static final int DATAGRAM_BUFFER_SIZE = 65535; //biggest size for no fragmentation
	private static final String TAG = UDPChannelBackend.class.getSimpleName();
	private final int port;
	private final IUDPChannelEventListener eventListener;
	public DatagramSocket datagramSocket;
	private volatile boolean isRunning = true;

	public UDPChannelBackend(int port, IUDPChannelEventListener eventListener) {
		this.port = port;
		this.eventListener = eventListener;
	}

	@Override
	public void run() {
		Log.d(TAG, "Starting UDP server");
		try {
			datagramSocket = new DatagramSocket(port);

			eventListener.onServerStarted();

			while (isRunning && !datagramSocket.isClosed() && !isInterrupted()) {
				byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				datagramSocket.receive(receivePacket);

				InetAddress ipAddress = receivePacket.getAddress();

				byte[] result = Arrays.copyOfRange(receivePacket.getData(), 0, receivePacket.getLength());
				Log.d(TAG, "UDP data received from: " + ipAddress.getHostAddress() + " data: " + Arrays.toString(result));
				eventListener.onDataReceived(ipAddress, result);
			}

			Log.d(TAG, "Stopping UDP server");

			datagramSocket.close();
		} catch (java.io.IOException e) {
			Log.e(TAG, "Datagram socket error: " + e.getMessage(), e);
		} finally {
			eventListener.onServerStopped();
			Log.i(TAG, "UDP server stopped.");
		}
	}

	public void sendData(final byte[] sendData, final InetAddress destination) {
		if (datagramSocket != null && !datagramSocket.isClosed()) {
			try {
				Log.d(TAG, "UDP data sending to " + destination.getHostAddress() + "data: " + Arrays.toString(sendData));
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, destination, port);
				datagramSocket.send(sendPacket);
			} catch (IOException e) {
				Log.d(TAG, "Failed to send udp data." + e.getMessage());
			}
		}
	}

	public void stopServer() {
		if (datagramSocket != null) {
			datagramSocket.close();
		}
		isRunning = false;
		interrupt();
	}

	public interface IUDPChannelEventListener {
		void onServerStarted();

		void onDataReceived(InetAddress sourceAddress, byte[] data);

		void onServerStopped();
	}
}
