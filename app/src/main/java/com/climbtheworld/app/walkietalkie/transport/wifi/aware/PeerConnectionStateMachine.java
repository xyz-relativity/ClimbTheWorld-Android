package com.climbtheworld.app.walkietalkie.transport.wifi.aware;

import java.util.HashMap;
import java.util.Map;

final class PeerConnectionStateMachine<K> {
	enum State {
		DISCOVERED,
		REQUESTING,
		AVAILABLE
	}

	private final Map<K, State> states = new HashMap<>();

	synchronized void markDiscovered(K peer) {
		states.putIfAbsent(peer, State.DISCOVERED);
	}

	synchronized boolean beginRequest(K peer) {
		State state = states.get(peer);
		if (state == State.REQUESTING || state == State.AVAILABLE) {
			return false;
		}
		states.put(peer, State.REQUESTING);
		return true;
	}

	synchronized boolean markAvailable(K peer) {
		if (states.get(peer) != State.REQUESTING) {
			return false;
		}
		states.put(peer, State.AVAILABLE);
		return true;
	}

	synchronized void requestFailed(K peer) {
		if (states.get(peer) == State.REQUESTING) {
			states.put(peer, State.DISCOVERED);
		}
	}

	synchronized State getState(K peer) {
		return states.get(peer);
	}

	synchronized void remove(K peer) {
		states.remove(peer);
	}

	synchronized void clear() {
		states.clear();
	}
}
