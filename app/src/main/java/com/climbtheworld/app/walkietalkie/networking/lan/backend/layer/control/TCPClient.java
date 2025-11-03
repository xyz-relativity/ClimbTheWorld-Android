package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient extends Thread {
	private static final String TAG = TCPServer.class.getSimpleName();
	private final Socket clientSocket;
	private ITCPClientListener eventsListener;
	private String uuid = "";
	private PrintWriter out;
	private BufferedReader in;

	private volatile boolean isRunning = false;

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

	public InetAddress getLocalIp() {
		return clientSocket.getLocalAddress();
	}

	public InetAddress getRemoteIp() {
		return clientSocket.getInetAddress();
	}

	@Override
	public void run() {
		try {
			isRunning = true;
			eventsListener.onTCPClientConnected(this);
			while (!isInterrupted() && isRunning) {
				String serverResponse;
				while ((serverResponse = in.readLine()) != null && !isInterrupted() && isRunning) {
					eventsListener.onControlMessageReceived(this, serverResponse);
				}

			}
		} catch (IOException e) {
			Log.e(TAG, "TCP client error: " + e.getMessage(), e);
		} finally {
			try {
				in.close();
				out.close();
				clientSocket.close();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
			}
			Log.i(TAG, "Tcp client disconnected: " + this.getRemoteIp().getHostAddress());
			eventsListener.onTCPClientDisconnected(this);
		}
	}

	public void sendControlMessage(String message) {
		out.println(message);
	}

	public void stopClient() {
		try {
			if (clientSocket != null) {
				clientSocket.close();
			}
			isRunning = false;
			interrupt();
		} catch (IOException e) {
			Log.i(TAG, e.getMessage(), e);
		}
	}

	public interface ITCPClientListener {
		void onTCPClientConnected(TCPClient client);

		void onControlMessageReceived(TCPClient client, String data);

		void onTCPClientDisconnected(TCPClient client);
	}
}
