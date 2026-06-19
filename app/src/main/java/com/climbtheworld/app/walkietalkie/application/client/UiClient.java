package com.climbtheworld.app.walkietalkie.application.client;

import com.climbtheworld.app.walkietalkie.ClientType;

public class UiClient {
	public String clientUUID;
	public String displayId;
	public String callSign = "";
	public int distance = -1;
	public ClientType type;

	public UiClient(String clientUUID, ClientType type) {
		this.type = type;
		this.clientUUID = clientUUID;
		this.displayId = clientUUID.substring(0, 8);
	}

	public interface IUiClientEvent {
		void notifyClientChange();
	}
}
