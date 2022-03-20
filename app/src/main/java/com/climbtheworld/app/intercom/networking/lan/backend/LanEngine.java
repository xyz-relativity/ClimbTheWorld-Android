package com.climbtheworld.app.intercom.networking.lan.backend;

import android.util.Log;

import com.climbtheworld.app.intercom.IClientEventListener;
import com.climbtheworld.app.intercom.networking.DataFrame;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public abstract class LanEngine {
	protected final String channel;
	protected final IClientEventListener clientHandler;

	protected LanEngine(String channel, IClientEventListener clientHandler) {
		this.channel = channel;
		this.clientHandler = clientHandler;
	}

	protected static List<String> getLocalIpAddress() {
		List<String> result = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces(); enumNetworkInterfaces.hasMoreElements(); ) {
				NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
				for (Enumeration<InetAddress> enumIpAddress = networkInterface.getInetAddresses(); enumIpAddress.hasMoreElements(); ) {
					InetAddress inetAddress = enumIpAddress.nextElement();
					if (!inetAddress.isLoopbackAddress() /* && inetAddress instanceof Inet4Address */) {
						result.add(inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			Log.d("======", "Failed to determine local address.", e);
		}
		return result;
	}

	public abstract void openNetwork(String serverAddress, int port);
	public abstract void closeNetwork();
	public abstract void sendData(DataFrame data);
}
