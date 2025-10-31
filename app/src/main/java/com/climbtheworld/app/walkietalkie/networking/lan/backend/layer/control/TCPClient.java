package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TCPClient extends Thread {
	private static final String TAG = TCPServer.class.getSimpleName();
	private final Socket clientSocket;
	private ITCPClientListener eventsListener;
	private String uuid = "";
	private PrintWriter out;
	private BufferedReader in;

	private TCPClient(Socket socket, ITCPClientListener eventsListener) {
		this.clientSocket = socket;
		this.eventsListener = eventsListener;

		try {
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	public static TCPClient buildFromSocket(Socket socket, ITCPClientListener eventsListener) {
		return new TCPClient(socket, eventsListener);
	}

	public static TCPClient connectToServer(String serverAddress, int port, ITCPClientListener eventsListener) throws IOException {
		Socket socket = new Socket(serverAddress, port);
		if (socket.getInetAddress().toString().compareTo(socket.getLocalAddress().toString()) > 0) {
			return null;
		}
		return new TCPClient(socket, eventsListener);
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public void setListener(ITCPClientListener eventsListener) {
		this.eventsListener = eventsListener;
	}

	public String getLocalIp() {
		return clientSocket.getLocalAddress().toString();
	}

	public String getRemoteIp() {
		return clientSocket.getInetAddress().toString();
	}

	@Override
	public void run() {
		try {
			eventsListener.onClientReady(this);
			while (!isInterrupted()) {
				try {
					String serverResponse;
					while ((serverResponse = in.readLine()) != null) {
						eventsListener.onDataReceived(this, serverResponse);
					}
				} catch (IOException e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}
		} finally {
			eventsListener.onClientDisconnected(this);
			try {
				in.close();
				out.close();
				clientSocket.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
		}
	}

	public void sendData(String message) {
		out.println(message);
	}

	public interface ITCPClientListener {
		void onClientReady(TCPClient client);

		void onDataReceived(TCPClient client, String data);

		void onClientDisconnected(TCPClient client);
	}
}
