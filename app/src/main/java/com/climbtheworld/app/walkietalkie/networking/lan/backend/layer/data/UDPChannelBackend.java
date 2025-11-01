package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.data;

import static com.climbtheworld.app.utils.constants.Constants.NETWORK_EXECUTOR;

import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.DataFrame;

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
	public DatagramSocket serverSocket;
	private volatile boolean isRunning = true;

	public UDPChannelBackend(int port, IUDPChannelEventListener eventListener) {
		this.port = port;
		this.eventListener = eventListener;
	}

	@Override
	public void run() {
		Log.d(TAG, "Starting UDP server");
		try {
			serverSocket = new DatagramSocket(port);

			eventListener.onServerStarted();

			while (isRunning && !serverSocket.isClosed() && !isInterrupted()) {
				byte[] receiveData = new byte[DATAGRAM_BUFFER_SIZE];
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

				serverSocket.receive(receivePacket);

				Log.d(TAG, "UDP data received: " + receivePacket);

				InetAddress ipAddress = receivePacket.getAddress();

				byte[] result = Arrays.copyOfRange(receivePacket.getData(), 0, receivePacket.getLength());
				notifyListeners(ipAddress.getHostAddress(), result);
			}

			Log.d(TAG, "Stopping UDP server");

			serverSocket.close();
			eventListener.onServerStopped();
		} catch (java.io.IOException e) {
			Log.d(TAG, "Failed to join multicast group.", e);
		}
	}

	public void sendData(final DataFrame sendData, final String destination) {
		NETWORK_EXECUTOR.execute(() -> {
			if (serverSocket != null && !serverSocket.isClosed()) {
				try {
					Log.d(TAG, "UDP data sending: " + sendData + " to: " + destination);
					DatagramPacket sendPacket = new DatagramPacket(sendData.toByteArray(), sendData.totalLength(), InetAddress.getByName(destination), port);
					serverSocket.send(sendPacket);
				} catch (IOException e) {
					Log.d(TAG, "Failed to send udp data." + e.getMessage());
				}
			}
		});
	}

	private void notifyListeners(String address, byte[] data) {
		eventListener.onDataReceived(address, data);
	}

	public void stopServer() {
		if (serverSocket != null) {
			serverSocket.close();
		}
		isRunning = false;
		interrupt();
	}

	public interface IUDPChannelEventListener {
		void onServerStarted();

		void onDataReceived(String sourceAddress, byte[] data);

		void onServerStopped();
	}
}
