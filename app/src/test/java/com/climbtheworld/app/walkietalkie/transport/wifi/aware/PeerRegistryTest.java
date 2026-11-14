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

	@Test
	public void callsignMessageAfterAMeasuredDistanceKeepsTheDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));

		// Our publisher gets the peer's callsign, and never knows the distance.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertFalse(change.newPeer);
		assertEquals(uuid, change.peer.uuid);
		assertEquals("new", change.peer.callsign);
		assertEquals(12.5, change.peer.distanceMeters, 0);

		// A retry of the message, or another callsign, does not hide it either.
		change = registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "newer", PubSubID.UNKNOWN_DISTANCE));

		assertEquals("newer", change.peer.callsign);
		assertEquals(12.5, change.peer.distanceMeters, 0);
	}

	@Test
	public void forgottenDistanceAfterACallsignMessageIsUnknown() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));
		registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		// The ranging stopped: our subscriber reports the peer again, without a distance.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", PubSubID.UNKNOWN_DISTANCE));

		assertEquals("new", change.peer.callsign);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);

		// The next message does not bring the forgotten distance back.
		change = registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);
	}

	@Test
	public void callsignMessageBeforeAnyMeasurementLeavesTheDistanceUnknown() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();

		// The message of a peer our subscriber has not found yet.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertTrue(change.newPeer);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);

		// Found, but not measured yet.
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", PubSubID.UNKNOWN_DISTANCE));
		change = registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);
	}

	@Test
	public void restartedPeerIsShownWithoutTheDistanceToItsOldHandles() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));
		registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		// Its message comes on a new handle: it restarted, and our subscriber has yet to find
		// and measure it again.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.PUBLISH, "pub2",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertEquals(set("sub", "pub"), new HashSet<>(change.staleHandles));
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);

		// Found again, the distance is the one our subscriber then reports.
		change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2",
				new PubSubID(uuid, "restarted", PubSubID.UNKNOWN_DISTANCE));
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);

		change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2",
				new PubSubID(uuid, "restarted", 3.5));
		assertEquals(3.5, change.peer.distanceMeters, 0);
	}

	@Test
	public void peerThatLeftComesBackWithoutItsOldDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));
		registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));
		registry.lose("sub");

		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.PUBLISH, "pub2",
				new PubSubID(uuid, "new", PubSubID.UNKNOWN_DISTANCE));

		assertTrue(change.newPeer);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.peer.distanceMeters, 0);
	}

	@Test
	public void lostPeerIsReportedWithoutItsLastDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "peer", 12.5));
		registry.register(PeerRegistry.Role.PUBLISH, "pub",
				new PubSubID(uuid, "peer", PubSubID.UNKNOWN_DISTANCE));

		// Its publisher is no longer discovered: the peer left.
		PeerRegistry.Change<String> change = registry.lose("sub");

		assertEquals(1, change.lostPeers.size());
		assertEquals(uuid, change.lostPeers.get(0).uuid);
		assertEquals("peer", change.lostPeers.get(0).callsign);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.lostPeers.get(0).distanceMeters, 0);
	}

	@Test
	public void peerLostToAnotherIdIsReportedWithoutItsLastDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "peer", 12.5));

		// Another device is found on the handle, with the distance measured to it.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(OTHER_PEER.uuid, "other", 3.5));

		assertEquals(1, change.lostPeers.size());
		assertEquals(uuid, change.lostPeers.get(0).uuid);
		assertEquals(PubSubID.UNKNOWN_DISTANCE, change.lostPeers.get(0).distanceMeters, 0);
		assertEquals(3.5, change.peer.distanceMeters, 0);
	}

	@Test
	public void clearReturnsEveryPeerWithoutItsLastDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "peer", 12.5));
		registry.register(PeerRegistry.Role.SUBSCRIBE, "other", OTHER_PEER);

		List<PubSubID> lostPeers = registry.clear();

		assertEquals(2, lostPeers.size());
		for (PubSubID lostPeer : lostPeers) {
			assertEquals(PubSubID.UNKNOWN_DISTANCE, lostPeer.distanceMeters, 0);
		}
		// One that had no distance is reported as it was registered.
		assertTrue(lostPeers.contains(OTHER_PEER));
	}

	@Test
	public void callsignSaidThroughAKnownHandleIsShownWithTheMeasuredDistance() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", 12.5));

		// The peer's publisher says its new callsign to our subscriber.
		PubSubID peer = registry.updateCallsign("sub", "new");

		assertEquals(uuid, peer.uuid);
		assertEquals("new", peer.callsign);
		assertEquals(12.5, peer.distanceMeters, 0);
	}

	@Test
	public void callsignSaidThroughAKnownHandleIsKept() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		PubSubID discovered = new PubSubID(uuid, "old", PubSubID.UNKNOWN_DISTANCE);
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", discovered);
		registry.updateCallsign("sub", "new");

		// Our subscriber measures the distance, and reports it with the callsign it knows.
		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub",
				new PubSubID(uuid, "old", 12.5));
		assertEquals("new", change.peer.callsign);
		assertEquals(12.5, change.peer.distanceMeters, 0);

		// The data path is recovered, or the peer is found again on the same handle: our
		// subscriber reports the peer as it was discovered.
		change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", discovered);
		assertFalse(change.newPeer);
		assertTrue(change.staleHandles.isEmpty());
		assertEquals("new", change.peer.callsign);

		// It is also the callsign the peer is lost with.
		change = registry.lose("sub");
		assertEquals(1, change.lostPeers.size());
		assertEquals("new", change.lostPeers.get(0).callsign);
	}

	@Test
	public void callsignSaidThroughAKnownHandleChangesNoHandle() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "old", -1));

		registry.updateCallsign("sub", "new");

		assertEquals(uuid, registry.getUUID("sub"));
		assertEquals(uuid, registry.getUUID("pub"));
		// Neither handle counts as a new one afterwards, which would be taken for a restart.
		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		assertTrue(change.staleHandles.isEmpty());
		change = registry.register(PeerRegistry.Role.PUBLISH, "pub", new PubSubID(uuid, "new", -1));
		assertFalse(change.newPeer);
		assertTrue(change.staleHandles.isEmpty());
		assertTrue(change.lostPeers.isEmpty());
	}

	@Test
	public void laterCallsignThroughAKnownHandleChangesItAgain() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.updateCallsign("sub", "new");

		// Back to the one it was discovered with, too.
		assertEquals("newer", registry.updateCallsign("sub", "newer").callsign);
		assertEquals("old", registry.updateCallsign("sub", "old").callsign);
	}

	@Test
	public void callsignOfAnUnknownHandleChangesNothing() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);

		assertNull(registry.updateCallsign("gone", "new"));

		assertNull(registry.getUUID("gone"));
		PeerRegistry.Change<String> change =
				registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", PEER);
		assertFalse(change.newPeer);
		assertEquals(PEER.callsign, change.peer.callsign);

		// A lost peer's handle is unknown as well.
		registry.lose("sub");
		assertNull(registry.updateCallsign("sub", "new"));
	}

	@Test
	public void peerFoundOnANewHandleIsShownAsPublishedUntilItSaysItsCallsignAgain() {
		PeerRegistry<String> registry = new PeerRegistry<>();
		UUID uuid = UUID.randomUUID();
		registry.register(PeerRegistry.Role.SUBSCRIBE, "sub", new PubSubID(uuid, "old", -1));
		registry.updateCallsign("sub", "new");

		PeerRegistry.Change<String> change = registry.register(PeerRegistry.Role.SUBSCRIBE, "sub2",
				new PubSubID(uuid, "published", -1));
		assertEquals(Collections.singletonList("sub"), change.staleHandles);
		assertEquals("published", change.peer.callsign);
		assertNull(registry.updateCallsign("sub", "stale"));

		assertEquals("new", registry.updateCallsign("sub2", "new").callsign);
	}

	private static HashSet<String> set(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}
}
