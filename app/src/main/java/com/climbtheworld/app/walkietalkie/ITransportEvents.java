package com.climbtheworld.app.walkietalkie;

import java.util.UUID;

public interface ITransportEvents {
	void onClientEvent(ITransportLayer transport, TransportPeer peer, ClientEvent event);

	void onData(UUID clientUUID, byte[] data);

	enum ClientEvent {
		CONNECT, UPDATE, DISCONNECT
	}

	class TransportPeer {
		public UUID clientUUID;
		public String callsign;
		public double distanceMeters;

		public TransportPeer(UUID clientUUID, String callsign, double distanceMeters) {
			this.clientUUID = clientUUID;
			this.callsign = callsign;
			this.distanceMeters = distanceMeters;
		}
	}
}
