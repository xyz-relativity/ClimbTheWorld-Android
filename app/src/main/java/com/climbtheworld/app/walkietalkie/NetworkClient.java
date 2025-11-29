package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.walkietalkie.networking.ConnectionState;

public abstract class NetworkClient {
	protected ConnectionState state = ConnectionState.AUTH;

	public ConnectionState getState() {
		return state;
	}
}
