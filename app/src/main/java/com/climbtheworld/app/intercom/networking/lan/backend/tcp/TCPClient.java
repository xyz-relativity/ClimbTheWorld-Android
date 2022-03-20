package com.climbtheworld.app.intercom.networking.lan.backend.tcp;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

public class TCPClient {
	private static final int SOCKET_BUFFER_SIZE = 1024;
	private final Socket socket;
	private volatile boolean isRunning = false;

	public Socket getSocket() {
		return socket;
	}

	public TCPClient(Socket socket) {
		this.socket = socket;
	}

	public void run() {
		byte[] buffer = new byte[SOCKET_BUFFER_SIZE];
		int bytes;
		isRunning = true;

		// Keep listening to the InputStream while connected
		while (isRunning && socket.isConnected()) {
			try {
				// Read from the InputStream
				bytes = socket.getInputStream().read(buffer);

				byte[] result = new byte[bytes];
				System.arraycopy(buffer, 0, result, 0, bytes);
			} catch (IOException e) {
				isRunning = false;
				Log.d("Bluetooth", "Client read fail.", e);
			}
		}
	}

	public void sendData(byte[] frame) {
		try {
			socket.getOutputStream().write(frame);
			socket.getOutputStream().flush();
		} catch (IOException e) {
			Log.d("Bluetooth", "Failed to send data", e);
		}
	}

	public void closeConnection() {
		if (socket.isConnected()) {
			try {
				if (socket.getInputStream() != null) {
					try {
						socket.getInputStream().close();
					} catch (Exception ignored) {
					}
				}

				if (socket.getOutputStream() != null) {
					try {
						socket.getOutputStream().close();
					} catch (Exception ignored) {
					}
				}

				try {
					socket.close();
				} catch (Exception ignored) {
				}

			} catch (IOException ignored) {
			}
		}

		isRunning = false;
	}
}
