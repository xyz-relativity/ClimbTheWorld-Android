package com.climbtheworld.app.walkietalkie;

public interface ITransportEvents {
	void onClientEvent(ITransportLayer transport, TransportPeer peer, ClientEvent event);

	enum ClientEvent {
		CONNECT, UPDATE, DISCONNECT
	}

	class TransportPeer {
		public String clientUUID;
		public String callsign;
		public double distanceMeters;

		public TransportPeer(String clientUUID, String callsign, double distanceMeters) {
			this.clientUUID = clientUUID;
			this.callsign = callsign;
			this.distanceMeters = distanceMeters;
		}
	}
}
