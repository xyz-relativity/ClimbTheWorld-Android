package com.climbtheworld.app.walkietalkie.networking.lan.backend.layer;

import android.util.Base64;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.control.TCPClient;
import com.climbtheworld.app.walkietalkie.networking.lan.backend.layer.data.UDPChannelBackend;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class NetworkNode implements TCPClient.ITCPClientListener {
	private static final String TAG = NetworkNode.class.getSimpleName();
	private final String channel;
	private final INetworkNodeEventListener eventListener;
	private final TCPClient tcpClient;
	private final UDPChannelBackend udpChannel;
	private NodeState state = NodeState.AUTH;

	public NetworkNode(String channel, TCPClient tcpClient, UDPChannelBackend udpChannel,
	                   INetworkNodeEventListener eventListener) {
		this.channel = channel;
		this.tcpClient = tcpClient;
		this.udpChannel = udpChannel;
		this.eventListener = eventListener;
		tcpClient.setListener(this);

		sendControl(computeDigest(channel + tcpClient.getRemoteIp()));
	}

	public void sendControl(String message) {
		tcpClient.sendControlMessage(state.command, message);
	}

	public void sendData(byte[] data) {
		udpChannel.sendData(data, getRemoteAddress());
	}

	@Override
	public void onTCPClientConnected(TCPClient client) {

	}

	@Override
	public void onControlMessageReceived(TCPClient client, String data) {
		switch (state) {
			case AUTH: {
				if (data.startsWith(state.command)) {
					String auth = data.split(state.command)[1];
					if (!auth.equals(computeDigest(channel + tcpClient.getLocalIp()))) {
						client.interrupt();
					}
					state = NodeState.ACTIVE;
					eventListener.onClientConnected(this);
				}
				return;
			}
			case ACTIVE: {
				if (data.startsWith(state.command)) {
					String message = data.split(state.command)[1];
					eventListener.onControlMessage(getRemoteAddress(), message);
				}
				return;
			}
		}

		if (data.startsWith(NodeState.DISCONNECTING.command)) {
			onTCPClientDisconnected(client);
		}
	}

	@Override
	public void onTCPClientDisconnected(TCPClient client) {
		eventListener.onClientDisconnected(this);
	}

	public InetAddress getRemoteAddress() {
		return tcpClient.getRemoteIp();
	}

	private String computeDigest(String message) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return Base64.encodeToString(md.digest(message.getBytes(StandardCharsets.UTF_8)),
					Base64.NO_WRAP);
		} catch (NoSuchAlgorithmException e) {
			Log.w(TAG, "Failed to calculate digest", e);
			return message;
		}
	}

	public void onDataReceived(InetAddress sourceAddress, byte[] data) {
		if (state != NodeState.ACTIVE) {
			return;
		}
		eventListener.onData(sourceAddress, data);
	}

	public void disconnect() {
		sendControl(NodeState.DISCONNECTING.command);
	}

	enum NodeState {
		AUTH("AUTH:"), IDENTITY("IDENTITY:"), ACTIVE("MESSAGE:"), DISCONNECTING("BYE!!");

		public final String command;

		NodeState(String command) {
			this.command = command;
		}
	}

	public interface INetworkNodeEventListener {
		void onClientConnected(NetworkNode networkNode);

		void onData(InetAddress sourceAddress, byte[] data);

		void onControlMessage(InetAddress sourceAddress, String message);

		void onClientDisconnected(NetworkNode networkNode);
	}
}
