package com.climbtheworld.app.walkietalkie.transport;

import com.climbtheworld.app.walkietalkie.transport.networking.ConnectionState;

public abstract class NetworkClient {
	protected ConnectionState state = ConnectionState.AUTH;

	public ConnectionState getState() {
		return state;
	}
}
