package com.climbtheworld.app.walkietalkie;

import java.util.List;

public interface ITransportEvents {
	void onClientUpdates(List<Client> clients);

	class Client {
		public String clientUUID;
		public String callsign;
		public int range;
	}
}
