package com.climbtheworld.app.walkietalkie;

import androidx.annotation.Nullable;

import java.util.HashMap;

public class ObservableHashMap<K,V> extends HashMap<K,V> {
	public interface MapChangeEventListener<K,V> {
		void onItemPut(K key, V value);
		void onItemRemove(K key, V value);
	}

	private MapChangeEventListener<K,V> eventListener = new MapChangeEventListener<K, V>() {
		@Override
		public void onItemPut(Object key, Object value) {
		}

		@Override
		public void onItemRemove(Object key, Object value) {
		}
	};

	// method to add listener
	public void addMapListener(MapChangeEventListener<K,V> eventListener) {
		this.eventListener = eventListener;
	}

	@Override
	public V put(K key, V value) {
		V ret = super.put(key,value);
		eventListener.onItemPut(key, value);
		return ret;
	}

	@Nullable
	@Override
	public V remove(@Nullable Object key) {
		V ret = super.remove(key);
		if (ret != null) {
			eventListener.onItemRemove((K) key, ret);
		}
		return ret;
	}

	@Override
	public boolean remove(@Nullable Object key, @Nullable Object value) {
		boolean ret = super.remove(key, value);
		if (ret) {
			eventListener.onItemRemove((K) key, (V) value);
		}
		return ret;
	}

	@Override
	public void clear() {
		for (Entry<K, V> entry : this.entrySet()) {
			eventListener.onItemRemove(entry.getKey(), entry.getValue());
		}

		super.clear();
	}
}