package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PeerConnectionStateMachineTest {
	@Test
	public void connectionTransitionsFromDiscoveredToRequestingToAvailable() {
		PeerConnectionStateMachine<String> connections = new PeerConnectionStateMachine<>();

		connections.markDiscovered("peer");
		assertEquals(PeerConnectionStateMachine.State.DISCOVERED,
				connections.getState("peer"));

		assertTrue(connections.beginRequest("peer"));
		assertEquals(PeerConnectionStateMachine.State.REQUESTING,
				connections.getState("peer"));

		assertTrue(connections.markAvailable("peer"));
		assertEquals(PeerConnectionStateMachine.State.AVAILABLE,
				connections.getState("peer"));
	}

	@Test
	public void duplicateRequestsAreRejectedWhileRequestingOrAvailable() {
		PeerConnectionStateMachine<String> connections = new PeerConnectionStateMachine<>();

		connections.markDiscovered("peer");
		assertTrue(connections.beginRequest("peer"));
		assertFalse(connections.beginRequest("peer"));
		assertTrue(connections.markAvailable("peer"));
		assertFalse(connections.beginRequest("peer"));
		assertFalse(connections.markAvailable("peer"));
	}

	@Test
	public void failedRequestCanBeRetried() {
		PeerConnectionStateMachine<String> connections = new PeerConnectionStateMachine<>();

		assertTrue(connections.beginRequest("peer"));
		connections.requestFailed("peer");

		assertEquals(PeerConnectionStateMachine.State.DISCOVERED,
				connections.getState("peer"));
		assertTrue(connections.beginRequest("peer"));
	}

	@Test
	public void rediscoveryDoesNotDowngradeAnAvailableConnection() {
		PeerConnectionStateMachine<String> connections = new PeerConnectionStateMachine<>();

		connections.beginRequest("peer");
		connections.markAvailable("peer");
		connections.markDiscovered("peer");

		assertEquals(PeerConnectionStateMachine.State.AVAILABLE,
				connections.getState("peer"));
	}

	@Test
	public void removingPeerClearsItsConnectionState() {
		PeerConnectionStateMachine<String> connections = new PeerConnectionStateMachine<>();

		connections.beginRequest("peer");
		connections.remove("peer");

		assertNull(connections.getState("peer"));
	}
}
