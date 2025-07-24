package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import com.climbtheworld.app.walkietalkie.networking.DataFrame;

public interface IUDPBackend {
	void startServer();
	void stopServer();
	void sendData(final DataFrame sendData, final String destination);
	void broadcastData(final DataFrame sendData);
}
