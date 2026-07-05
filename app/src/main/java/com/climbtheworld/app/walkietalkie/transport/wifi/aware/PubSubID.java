package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.UUID;

public class PubSubID {
	public final UUID uuid;
	public final String callsign;
	public final Double distanceMeters;

	public PubSubID(UUID uuid, String callsign, double distanceMeters) {
		this.uuid = uuid;
		this.callsign = callsign;
		this.distanceMeters = distanceMeters;
	}
}
