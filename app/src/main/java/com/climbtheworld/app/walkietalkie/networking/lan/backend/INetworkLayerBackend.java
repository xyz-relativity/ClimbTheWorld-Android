package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import java.net.InetAddress;

public interface INetworkLayerBackend {
	void startServer();
	void stopServer();

	interface IEventListener {
		void onClientConnected(InetAddress host);
		void onClientDisconnected(InetAddress host);
	}
}
