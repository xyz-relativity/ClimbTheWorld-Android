package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.UUID;

final class PeerConnectionElection {
	private PeerConnectionElection() {
	}

	static boolean localInitiates(UUID localUUID, UUID peerUUID) {
		return localUUID.compareTo(peerUUID) < 0;
	}
}
