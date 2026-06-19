package com.climbtheworld.app.walkietalkie.application.client;

import com.climbtheworld.app.walkietalkie.ClientType;

public class UiClient {
	public String clientUUID;
	public String callSign = "";
	public int distance = -1;
	public ClientType type;

	public UiClient(String clientUUID, String callSign, ClientType type, int distance) {
		this.type = type;
		this.clientUUID = clientUUID;
		this.callSign = callSign;
		this.distance = distance;
	}

	public interface IUiClientEvent {
		void notifyClientChange();
	}
}
