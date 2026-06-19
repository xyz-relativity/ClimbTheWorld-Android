package com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer;

import android.util.Base64;
import android.util.Log;

import com.climbtheworld.app.walkietalkie.transport.NetworkClient;
import com.climbtheworld.app.walkietalkie.transport.networking.ConnectionState;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer.control.TCPClient;
import com.climbtheworld.app.walkietalkie.transport.networking.lan.backend.layer.data.UDPChannelBackend;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class IpNetworkNode extends NetworkClient implements TCPClient.ITCPClientListener {
	private static final String TAG = IpNetworkNode.class.getSimpleName();
	private final String channel;
	private final INetworkNodeEventListener eventListener;
	private final TCPClient tcpClient;
	private final UDPChannelBackend udpChannel;

	public IpNetworkNode(String channel, TCPClient tcpClient, UDPChannelBackend udpChannel,
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
		if (data.startsWith(ConnectionState.DISCONNECTING.command)) {
			onTCPClientDisconnected(client);
			return;
		}

		switch (state) {
			case AUTH: {
				if (data.startsWith(state.command)) {
					String auth = data.split(state.command)[1];
					if (!auth.equals(computeDigest(channel + tcpClient.getLocalIp()))) {
						client.interrupt();
					}
					state = ConnectionState.ACTIVE;
					eventListener.onClientConnected(this);
				}
				return;
			}
			case ACTIVE: {
				if (data.startsWith(state.command)) {
					String message = data.split(state.command)[1];
					eventListener.onControlMessage(this, message);
				}
			}
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
		if (state != ConnectionState.ACTIVE) {
			return;
		}
		eventListener.onData(this, data);
	}

	public void disconnect() {
		tcpClient.sendControlMessage(ConnectionState.DISCONNECTING.command, "");
	}

	public String getUUID() {
		return tcpClient.getUuid();
	}

	public ConnectionState getState() {
		return state;
	}

	public interface INetworkNodeEventListener {
		void onClientConnected(IpNetworkNode ipNetworkNode);

		void onData(IpNetworkNode source, byte[] data);

		void onControlMessage(IpNetworkNode source, String message);

		void onClientDisconnected(IpNetworkNode ipNetworkNode);
	}
}
