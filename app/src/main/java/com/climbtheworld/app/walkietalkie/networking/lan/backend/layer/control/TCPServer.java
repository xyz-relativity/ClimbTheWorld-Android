package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer extends Thread {
	private static final String TAG = TCPServer.class.getSimpleName();
	private final ITCPServerListener eventsListener;
	private final int port;
	private ServerSocket serverSocket;
	private volatile boolean isRunning = false;

	public TCPServer(int port, ITCPServerListener eventsListener) {
		this.port = port;
		this.eventsListener = eventsListener;
	}

	@Override
	public void run() {
		try {
			serverSocket = new ServerSocket(port);
			eventsListener.onTCPServerStarted();
			isRunning = true;
			while (isRunning && !isInterrupted() && serverSocket.isBound()) {
				Socket clientSocket = serverSocket.accept();
				Log.i(TAG, "New client connected: " + clientSocket);

				eventsListener.onTCPClientConnected(clientSocket);
			}
			serverSocket.close();
		} catch (IOException e) {
			Log.e(TAG, "Server error: " + e.getMessage(), e);
		} finally {
			eventsListener.onTCPServerStopped();
			Log.i(TAG, "TCP server stopped.");
		}
	}

	public void stopServer() {
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
			isRunning = false;
			interrupt();
		} catch (IOException e) {
			Log.i(TAG, e.getMessage(), e);
		}
	}

	public interface ITCPServerListener {
		void onTCPServerStarted();

		void onTCPClientConnected(Socket clientSocket);

		void onTCPServerStopped();
	}
}
