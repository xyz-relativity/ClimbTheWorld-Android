package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class PeerRegistryTest {
	private static final PubSubID PEER = new PubSubID(UUID.randomUUID(), "peer", -1);
	private static final PubSubID OTHER_PEER = new PubSubID(UUID.randomUUID(), "other", -1);

	@Test
	public void firstHandleOfAPeerIsANewPeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		assertTrue(change.newPeer);
		assertTrue(change.staleHandles.isEmpty());
		assertEquals(PEER.uuid, registry.getUUID("sub"));
	}

	@Test
	public void sameHandleAgainIsAnUpdate() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		assertFalse(change.newPeer);
		assertTrue(change.staleHandles.isEmpty());
		assertTrue(change.lostPeers.isEmpty());
	}

	@Test
	public void handleInTheOtherRoleBelongsToTheSamePeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		assertFalse(change.newPeer);
		assertTrue(change.staleHandles.isEmpty());
		assertEquals(PEER.uuid, registry.getUUID("pub"));
	}

	@Test
	public void rediscoveryOnANewHandleDropsEveryOldHandle() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2", PEER);

		assertFalse(change.newPeer);
		assertEquals(set("sub", "pub"), new HashSet<>(change.staleHandles));
		assertTrue(change.lostPeers.isEmpty());
		assertNull(registry.getUUID("sub"));
		assertNull(registry.getUUID("pub"));
		assertEquals(PEER.uuid, registry.getUUID("sub2"));
	}

	@Test
	public void newPublishHandleAlsoDetectsARestart() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.PUBLISH, "pub2", PEER);

		assertEquals(set("sub", "pub"), new HashSet<>(change.staleHandles));
		assertTrue(change.lostPeers.isEmpty());

		// The restarted peer's publisher is then found without another reset.
		change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2", PEER);
		assertTrue(change.staleHandles.isEmpty());
	}

	@Test
	public void losingThePublisherDropsThePeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change = registry.lose("sub");

		assertEquals(set("sub", "pub"), new HashSet<>(change.staleHandles));
		assertEquals(Collections.singletonList(PEER), change.lostPeers);
		assertNull(registry.getUUID("pub"));
	}

	@Test
	public void losingTheSubscriberKeepsAStillDiscoveredPeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change = registry.lose("pub");

		assertEquals(Collections.singletonList("pub"), change.staleHandles);
		assertTrue(change.lostPeers.isEmpty());
		assertEquals(PEER.uuid, registry.getUUID("sub"));
	}

	@Test
	public void losingTheOnlyHandleDropsThePeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change = registry.lose("pub");

		assertEquals(Collections.singletonList(PEER), change.lostPeers);
	}

	@Test
	public void losingAnUnknownHandleChangesNothing() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		PeerRegistry.Change<String> change = registry.lose("gone");

		assertTrue(change.staleHandles.isEmpty());
		assertTrue(change.lostPeers.isEmpty());
		assertEquals(PEER.uuid, registry.getUUID("sub"));
	}

	@Test
	public void handleAnnouncingAnotherIdReplacesTheOldPeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", OTHER_PEER);

		assertTrue(change.newPeer);
		assertEquals(Collections.singletonList("pub"), change.staleHandles);
		assertEquals(Collections.singletonList(PEER), change.lostPeers);
		assertEquals(OTHER_PEER.uuid, registry.getUUID("sub"));
	}

	@Test
	public void clearReturnsEveryPeer() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		registry.register(PeerRegistry.Role.PUBLISH, "pub", PEER);
		registry.register(PeerRegistry.Role.SUBSCRIBE, "other", OTHER_PEER);

		List<PubSubID> lostPeers = registry.clear();

		assertEquals(new HashSet<>(Arrays.asList(PEER, OTHER_PEER)), new HashSet<>(lostPeers));
		assertNull(registry.getUUID("sub"));
		assertTrue(registry.lose("other").lostPeers.isEmpty());
	}

	@Test
	public void registeredPeerIsTheOneReported() {
		PeerRegistry<String> registry = new PeerRegistry<>();

		assertSame(PEER, registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER).peer);
		assertNull(registry.lose("sub").peer);
	}

	@Test
	public void distanceAfterACallsignChangeKeepsTheNewCallsign() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));

		// The peer says its new callsign in a message to our publisher.
		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));
		assertEquals("new", change.peer.callsign);

		// Our subscriber then measures the distance, and reports it with the callsign it knows.
		change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", 12.5));

		assertFalse(change.newPeer);
		assertEquals(uuid, change.peer.uuid);
		assertEquals("new", change.peer.callsign);
		assertEquals(12.5, change.peer.distanceMeters, 0);
	}

	@Test
	public void forgottenDistanceAndRediscoveryKeepTheNewCallsign() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));

		// Both come from our subscriber as a peer with the discovered callsign and no distance.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", PubSubID.UNKNOWN_DISTANCE));

		assertEquals("new", change.peer.callsign);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);
	}

	@Test
	public void messageBeforeDiscoveryKeepsItsCallsign() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));

		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", -1));

		assertEquals("new", change.peer.callsign);
	}

	@Test
	public void laterMessageChangesTheCallsignAgain() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));

		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "newer", -1));

		assertEquals("newer", change.peer.callsign);
	}

	@Test
	public void discoveredCallsignCountsWhileThePeerSentNone() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));

		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "renamed", -1));

		assertEquals("renamed", change.peer.callsign);
	}

	@Test
	public void restartedPeerIsShownWithTheCallsignItPublishes() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));

		// Found on a new handle: its discovery info is newer than the message it sent before.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2",
				new PubSubID(uuid, "restarted", -1));

		assertEquals("restarted", change.peer.callsign);
	}

	@Test
	public void lostPeerIsReportedWithItsCurrentCallsign() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));

		PeerRegistry.Change<String> change = registry.lose("sub");

		assertEquals(1, change.lostPeers.size());
		assertEquals("new", change.lostPeers.get(0).callsign);
	}

	private static HashSet<String> set(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}
}
