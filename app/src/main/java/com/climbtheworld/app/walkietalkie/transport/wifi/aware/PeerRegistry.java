package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which discovery handles belong to which peer. A peer keeps its ID across Wi-Fi Aware
 * restarts while its handles change, so a known ID showing up on a new handle means every older
 * handle of that peer is dead.
 * <p>
 * It also holds what is shown of each peer. Both sessions report a peer, each with the callsign
 * it learnt and only one of them with a distance, so what they report is only shown as the
 * registry gives it back, see register.
 */
final class PeerRegistry<H> {
	enum Role {
		// The peer's publisher, found by our subscriber.
		SUBSCRIBE,
		// The peer's subscriber, which messaged our publisher.
		PUBLISH
	}

	static final class Change<H> {
		// Handles whose sessions and data paths must be torn down.
		final List<H> staleHandles = new ArrayList<>();
		// Peers left without any handle.
		final List<PubSubID> lostPeers = new ArrayList<>();
		boolean newPeer;
		// The registered peer as it is to be shown, null when none was registered.
		PubSubID peer;
	}

	private static final class Peer<H> {
		private PubSubID id;
		private H subscribeHandle;
		private H publishHandle;
		// Whether the peer sent its callsign in a message since it was last found, see register.
		private boolean callsignFromMessage;
		// The distance our subscriber last reported to the handles the peer has now, see register.
		private double distanceMeters = PubSubID.UNKNOWN_DISTANCE;
	}

	private final Map<UUID, Peer<H>> peers = new HashMap<>();
	private final Map<H, UUID> handleOwners = new HashMap<>();

	/**
	 * Registers a peer as one of the sessions reports it: found, found again, or changed.
	 * <p>
	 * The callsign our subscriber reports is the one of the peer's discovery info, which dates
	 * from when the peer started publishing, and it reports it again with every distance it
	 * measures or forgets. A message of the peer carries its callsign of the moment. So once a
	 * peer has sent one, only its messages change its callsign: a distance or a rediscovery
	 * would otherwise bring back the callsign it had before.
	 * <p>
	 * The distance is the mirror of it. Only our subscriber measures one, while our publisher,
	 * which gets the messages, never knows it. So only our subscriber changes the distance, to the
	 * one it measured or to unknown once it forgot it: a message would otherwise hide the measured
	 * distance until the next ranging.
	 *
	 * @return what the registration changed, with the peer as it is to be shown
	 */
	synchronized Change<H> register(Role role, H handle, PubSubID id) {
		Change<H> change = new Change<>();
		UUID owner = handleOwners.get(handle);
		if (owner != null && !owner.equals(id.uuid)) {
			removeHandle(owner, handle, change);
		}

		Peer<H> peer = peers.get(id.uuid);
		if (peer == null) {
			peer = new Peer<>();
			peers.put(id.uuid, peer);
			change.newPeer = true;
		} else {
			H current = role == Role.SUBSCRIBE ? peer.subscribeHandle : peer.publishHandle;
			if (current != null && !current.equals(handle)) {
				// Rediscovered on a new handle: the peer restarted, so all its old handles are dead.
				dropHandles(peer, change);
			}
		}

		if (role == Role.PUBLISH) {
			peer.callsignFromMessage = true;
			if (id.distanceMeters != peer.distanceMeters) {
				id = new PubSubID(id.uuid, id.callsign, peer.distanceMeters);
			}
		} else {
			peer.distanceMeters = id.distanceMeters;
			if (peer.callsignFromMessage && !peer.id.callsign.equals(id.callsign)) {
				id = new PubSubID(id.uuid, peer.id.callsign, id.distanceMeters);
			}
		}
		peer.id = id;
		change.peer = id;
		if (role == Role.SUBSCRIBE) {
			peer.subscribeHandle = handle;
		} else {
			peer.publishHandle = handle;
		}
		handleOwners.put(handle, id.uuid);
		change.staleHandles.remove(handle);
		return change;
	}

	/**
	 * Takes the callsign a peer said in a message that is no part of the handshake. The message
	 * came through a handle of the peer registered already, so unlike register, this finds no
	 * peer and no restart, and leaves the handles and the distance as they are. The callsign is
	 * then kept as one register got in a message.
	 *
	 * @return the peer as it is to be shown, null when the handle is of no peer
	 */
	synchronized PubSubID updateCallsign(H handle, String callsign) {
		UUID owner = handleOwners.get(handle);
		if (owner == null) {
			return null;
		}

		Peer<H> peer = peers.get(owner);
		peer.callsignFromMessage = true;
		if (!peer.id.callsign.equals(callsign)) {
			peer.id = new PubSubID(owner, callsign, peer.distanceMeters);
		}
		return peer.id;
	}

	synchronized Change<H> lose(H handle) {
		Change<H> change = new Change<>();
		UUID owner = handleOwners.get(handle);
		if (owner != null) {
			removeHandle(owner, handle, change);
		}
		return change;
	}

	synchronized UUID getUUID(H handle) {
		return handleOwners.get(handle);
	}

	synchronized List<PubSubID> clear() {
		List<PubSubID> lostPeers = new ArrayList<>();
		for (Peer<H> peer : peers.values()) {
			lostPeers.add(peer.id);
		}
		peers.clear();
		handleOwners.clear();
		return lostPeers;
	}

	private void removeHandle(UUID owner, H handle, Change<H> change) {
		Peer<H> peer = peers.get(owner);
		if (handle.equals(peer.subscribeHandle)) {
			// Its publisher is no longer discoverable, so the peer left: drop all its handles.
			dropHandles(peer, change);
		} else {
			handleOwners.remove(handle);
			peer.publishHandle = null;
			change.staleHandles.add(handle);
		}

		if (peer.subscribeHandle == null && peer.publishHandle == null) {
			peers.remove(owner);
			change.lostPeers.add(peer.id);
		}
	}

	private void dropHandles(Peer<H> peer, Change<H> change) {
		// The peer restarted or left: what it publishes when found again is newer than the
		// messages it sent before, and the distance was measured to the handles dropped here, so
		// it is unknown until our subscriber measures it again.
		peer.callsignFromMessage = false;
		peer.distanceMeters = PubSubID.UNKNOWN_DISTANCE;
		if (peer.subscribeHandle != null) {
			handleOwners.remove(peer.subscribeHandle);
			change.staleHandles.add(peer.subscribeHandle);
			peer.subscribeHandle = null;
		}
		if (peer.publishHandle != null) {
			handleOwners.remove(peer.publishHandle);
			change.staleHandles.add(peer.publishHandle);
			peer.publishHandle = null;
		}
	}
}
