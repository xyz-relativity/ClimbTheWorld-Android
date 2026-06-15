package com.climbtheworld.app.walkietalkie.backend.networking;

public enum ConnectionState {
	AUTH("AUTH:"),
	IDENTITY("IDENTITY:"),
	ACTIVE("MESSAGE:"),
	DISCONNECTING("BYE!!");

	public final String command;

	ConnectionState(String command) {
		this.command = command;
	}
}
