package com.climbtheworld.app.walkietalkie.networking.lan.backend;

import com.climbtheworld.app.walkietalkie.networking.DataFrame;

public interface IDataLayerLayerBackend extends INetworkLayerBackend {
	void sendData(final DataFrame sendData, final String destination);
}
