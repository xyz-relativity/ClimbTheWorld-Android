package com.climbtheworld.app.intercom.networking.lan.backend.tcp;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {
	public static final int DATAGRAM_BUFFER_SIZE = 1024; //biggest size for no fragmentation

	private ServerThread server;

	public TCPServer(LanTCPEngine.ITCPEventListener itcpEventListener) {
	}

	public void startServer(int port) {
		stopServer();
		server = new ServerThread(port);
		server.start();
	}

	class ServerThread extends Thread {
		private final Integer serverPort;
		private volatile boolean isRunning = true;
		ServerSocket serverSocket;

		public ServerThread(int port) {
			this.serverPort = port;
		}

		@Override
		public void run() {
			isRunning = true;
			try {
				serverSocket = new ServerSocket(serverPort);
			} catch (IOException e) {
				Log.d("TCP", "Failed to create socket." + e.getMessage());
				return;
			}

			while (isRunning && serverSocket.isBound()) {
				try {
					Socket client = serverSocket.accept();
				} catch (IOException e) {
					Log.d("TCP", "Failed to accept client." + e.getMessage());
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
