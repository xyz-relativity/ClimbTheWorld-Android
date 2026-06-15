package com.climbtheworld.app.walkietalkie.backend;

import com.climbtheworld.app.walkietalkie.backend.networking.ConnectionState;

public abstract class NetworkClient {
	protected ConnectionState state = ConnectionState.AUTH;

	public ConnectionState getState() {
		return state;
	}
}
