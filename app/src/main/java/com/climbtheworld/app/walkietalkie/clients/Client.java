package com.climbtheworld.app.walkietalkie.clients;

public class Client {
	private final ClientType clientType = ClientType.GENERIC;

	public ClientType getClientType() {
		return clientType;
	}

	public enum ClientStatus {
		DISCOVERED, AUTH, CONNECTED, DISCONNECTING
	}
}
