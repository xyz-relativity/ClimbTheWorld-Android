package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class PeerConnectionElectionTest {
	@Test
	public void localInitiatesSelectsExactlyOneDevicePerPair() {
		UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

		boolean firstInitiates = PeerConnectionElection.localInitiates(first, second);
		boolean secondInitiates = PeerConnectionElection.localInitiates(second, first);

		assertNotEquals(firstInitiates, secondInitiates);
	}

	@Test
	public void localInitiatesCreatesOnePathForEveryPairInFiveDeviceGroup() {
		List<UUID> devices = Arrays.asList(
				UUID.fromString("00000000-0000-0000-0000-000000000001"),
				UUID.fromString("00000000-0000-0000-0000-000000000002"),
				UUID.fromString("00000000-0000-0000-0000-000000000003"),
				UUID.fromString("00000000-0000-0000-0000-000000000004"),
				UUID.fromString("00000000-0000-0000-0000-000000000005"));
		int electedPaths = 0;

		for (int first = 0; first < devices.size(); first++) {
			for (int second = first + 1; second < devices.size(); second++) {
				UUID firstDevice = devices.get(first);
				UUID secondDevice = devices.get(second);
				boolean firstInitiates = PeerConnectionElection.localInitiates(
						firstDevice, secondDevice);
				boolean secondInitiates = PeerConnectionElection.localInitiates(
						secondDevice, firstDevice);

				assertNotEquals(firstInitiates, secondInitiates);
				electedPaths++;
			}
		}

		assertEquals(10, electedPaths);
	}

	@Test
	public void localInitiatesRejectsSelfConnection() {
		UUID device = UUID.fromString("00000000-0000-0000-0000-000000000001");

		assertFalse(PeerConnectionElection.localInitiates(device, device));
	}
}
