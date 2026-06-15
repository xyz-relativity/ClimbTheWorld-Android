package com.climbtheworld.app.walkietalkie.frontend;

import com.climbtheworld.app.walkietalkie.frontend.clients.ClientType;

public class UiClient {
	public String clientUUID;
	public String displayId;
	public String callSign = "";
	public int distance = -1;
	public ClientType type;

	public UiClient(ClientType type, String clientUUID) {
		this.type = type;
		this.clientUUID = clientUUID;
		this.displayId = clientUUID.substring(0, 8);
	}

	public interface IUiClientEvent {
		void notifyClientChange();
	}
}
