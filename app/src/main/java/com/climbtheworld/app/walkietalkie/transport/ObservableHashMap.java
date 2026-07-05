package com.climbtheworld.app.walkietalkie.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ObservableHashMap<K, V> {
	private final HashMap<K, V> internalMap = new HashMap<>();
	private MapChangeListener<K, V> listener;

	public void setListener(MapChangeListener<K, V> listener) {
		this.listener = listener;
	}

	public void put(K key, V value) {
		// Determine if this is an ADDED or UPDATED event
		boolean containsKey = internalMap.containsKey(key);
		MapEvent event = containsKey ? MapEvent.UPDATED : MapEvent.ADDED;

		internalMap.put(key, value);

		if (listener != null) {
			listener.onMapChanged(key, value, event);
		}
	}

	public void remove(K key) {
		// Capture the value before removing it so we can hand it to the listener
		V removedValue = internalMap.remove(key);

		// Only notify if the key actually existed and a value was removed
		if (listener != null && removedValue != null) {
			listener.onMapChanged(key, removedValue, MapEvent.REMOVED);
		}
	}

	public void clear() {
		if (listener != null) {
			// Loop through existing entries to pass the actual data being deleted
			for (Map.Entry<K, V> entry : internalMap.entrySet()) {
				listener.onMapChanged(entry.getKey(), entry.getValue(), MapEvent.REMOVED);
			}
		}
		internalMap.clear();
	}

	public V get(K key) {
		return internalMap.get(key);
	}

	public Set<K> keySet() {
		return internalMap.keySet();
	}

	public Set<Map.Entry<K, V>> entrySet() {
		return internalMap.entrySet();
	}

	// 1. Define the event states
	public enum MapEvent {
		ADDED,
		UPDATED,
		REMOVED
	}

	// 2. Update the listener interface to include the state and the affected data
	public interface MapChangeListener<K, V> {
		void onMapChanged(K key, V value, MapEvent event);
	}
}
