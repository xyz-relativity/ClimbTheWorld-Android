package com.climbtheworld.app.walkietalkie.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ObservableHashMap<K, V> {
	private final HashMap<K, V> internalMap = new HashMap<>();
	private MapChangeListener<K, V> listener;

	public synchronized void setListener(MapChangeListener<K, V> listener) {
		this.listener = listener;
	}

	public void put(K key, V value) {
		MapEvent event;
		MapChangeListener<K, V> currentListener;
		synchronized (this) {
			event = internalMap.containsKey(key) ? MapEvent.UPDATED : MapEvent.ADDED;
			internalMap.put(key, value);
			currentListener = listener;
		}

		if (currentListener != null) {
			currentListener.onMapChanged(key, value, event);
		}
	}

	public boolean replaceIfSame(K key, V expectedValue, V newValue) {
		MapChangeListener<K, V> currentListener;
		synchronized (this) {
			if (internalMap.get(key) != expectedValue) {
				return false;
			}
			internalMap.put(key, newValue);
			currentListener = listener;
		}

		if (currentListener != null) {
			currentListener.onMapChanged(key, newValue, MapEvent.UPDATED);
		}
		return true;
	}

	public void remove(K key) {
		V removedValue;
		MapChangeListener<K, V> currentListener;
		synchronized (this) {
			removedValue = internalMap.remove(key);
			currentListener = listener;
		}

		if (currentListener != null && removedValue != null) {
			currentListener.onMapChanged(key, removedValue, MapEvent.REMOVED);
		}
	}

	public void clear() {
		Map<K, V> removedEntries;
		MapChangeListener<K, V> currentListener;
		synchronized (this) {
			removedEntries = new HashMap<>(internalMap);
			internalMap.clear();
			currentListener = listener;
		}

		if (currentListener != null) {
			for (Map.Entry<K, V> entry : removedEntries.entrySet()) {
				currentListener.onMapChanged(entry.getKey(), entry.getValue(), MapEvent.REMOVED);
			}
		}
	}

	public synchronized V get(K key) {
		return internalMap.get(key);
	}

	public synchronized Map<K, V> snapshot() {
		return new HashMap<>(internalMap);
	}

	public synchronized Set<K> keySet() {
		return new HashMap<>(internalMap).keySet();
	}

	public synchronized Set<Map.Entry<K, V>> entrySet() {
		return new HashMap<>(internalMap).entrySet();
	}

	public enum MapEvent {
		ADDED,
		UPDATED,
		REMOVED
	}

	public interface MapChangeListener<K, V> {
		void onMapChanged(K key, V value, MapEvent event);
	}
}
