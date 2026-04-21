package com.climbtheworld.app.walkietalkie.clients;

import java.util.HashMap;
import java.util.Map;

public class ClientManager {
	private final Map<String, Client> clientList = new HashMap<>();

	public void addClient(String uuid, Client client) {
		clientList.put(uuid, client);
	}

	public void removeClient(String uuid) {
		clientList.remove(uuid);
	}
}
