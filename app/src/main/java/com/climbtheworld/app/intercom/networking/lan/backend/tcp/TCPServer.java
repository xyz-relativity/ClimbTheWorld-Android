package com.climbtheworld.app.intercom.networking.lan.backend.tcp;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation
	private final Integer serverPort;
	private ServerThread server;

	public TCPServer (int port) {
		this.serverPort = port;
	}

	public void startServer() {
		stopServer();
		server = new ServerThread();
		server.start();
	}

	class ServerThread extends Thread {

		private volatile boolean isRunning = true;
		ServerSocket serverSocket;

		@Override
		public void run() {
			isRunning = true;
			try {
				serverSocket = new ServerSocket(serverPort);
			} catch (IOException e) {
				Log.d("Bluetooth", "Failed to create socket.", e);
				return;
			}

			while (isRunning && serverSocket.isBound()) {
				try {
					Socket client = serverSocket.accept();
				} catch (IOException e) {
					Log.d("Bluetooth", "Failed to accept client.", e);
				}
			}
		}

		void stopServer() {
			try {
				serverSocket.close();
			} catch (IOException ignore) {
			}
			isRunning = false;
		}
	}

	public void stopServer() {
		if (server != null) {
			server.stopServer();
			server = null;
		}
	}
}
