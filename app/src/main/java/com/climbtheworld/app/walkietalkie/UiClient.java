package com.climbtheworld.app.walkietalkie;

import com.climbtheworld.app.walkietalkie.clients.ClientType;

public class UiClient {
	public String clientId;
	public String displayId;
	public String callSign = "";
	public int distance = 0;
	public ClientType type;

	public UiClient(ClientType type, String clientId) {
		this.type = type;
		this.clientId = clientId;
		this.displayId = clientId.substring(0, 8);
	}

	public interface IUiClientEvent {
		void notifyClientChange();
	}
}
