package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer;

import android.util.Base64;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NetworkNode implements TCPClient.ITCPClientListener {
	private static final String TAG = NetworkNode.class.getSimpleName();
	private final String channel;
	private final INetworkNodeEventListener eventListener;
	private final TCPClient tcpClient;
	private NodeState state = NodeState.AUTH;

	public NetworkNode(String channel, TCPClient tcpClient, INetworkNodeEventListener eventListener) {
		this.tcpClient = tcpClient;
		this.eventListener = eventListener;
		this.channel = channel;
		tcpClient.setListener(this);

		sendControl(state.command, computeDigest(channel + tcpClient.getRemoteIp()));
	}

	public void sendControl(String command, String data) {
		tcpClient.sendData(command + data);
	}

	public void sendData(String data) {

	}

	@Override
	public void onClientReady(TCPClient client) {

	}

	@Override
	public void onDataReceived(TCPClient client, String data) {
		switch (state) {
			case AUTH: {
				if (data.startsWith(state.command)) {
					String auth = data.split(state.command)[1];
					if (!auth.equals(computeDigest(channel + tcpClient.getLocalIp()))) {
						client.interrupt();
					}
					state = NodeState.ACTIVE;
				}
			} break;
			case ACTIVE: {
				if (data.startsWith(state.command)) {
					String message = data.split(state.command)[1];
					eventListener.onControlMessage(message);
				}
			}
		}
	}

	@Override
	public void onClientDisconnected(TCPClient client) {
		eventListener.onClientDisconnected(this);
	}

	public String getRemoteAddress() {
		return tcpClient.getRemoteIp();
	}

	private String computeDigest(String message) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return Base64.encodeToString(md.digest(message.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
		} catch (NoSuchAlgorithmException e) {
			Log.w(TAG, "Failed to calculate digest", e);
			return message;
		}
	}

	enum NodeState {
		AUTH("AUTH:"), IDENTITY("IDENTITY:"), ACTIVE("MESSAGE:"), DISCONNECTING("BYE");

		public final String command;

		NodeState(String command) {
			this.command = command;
		}
	}

	public interface INetworkNodeEventListener {
		void onData(String data);
		void onControlMessage(String message);
		void onClientDisconnected(NetworkNode networkNode);
	}
}
