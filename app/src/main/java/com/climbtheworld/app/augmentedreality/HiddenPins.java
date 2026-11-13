package com.climbtheworld.app.augmentedreality;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The pins kept while they are out of view, to be shown again as they are rather than built
 * anew. Only so many are kept: past that, the ones hidden the longest go first, as the view
 * mostly turns back to what it just left.
 *
 * @param <K> what a pin is of
 * @param <V> the pin
 */
final class HiddenPins<K, V> {
	private final int capacity;
	// In the order they were hidden, the longest first.
	private final LinkedHashMap<K, V> pins = new LinkedHashMap<>();

	/**
	 * @param capacity how many pins are kept at most
	 */
	HiddenPins(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * Keeps a pin that went out of view.
	 *
	 * @return the pin this leaves no room for, which the caller is to drop: the one hidden the
	 * longest, or another one kept for the same key; null when there is none
	 */
	V hide(K key, V pin) {
		// Taken out first, as a key put again would keep its place among the longest hidden.
		V replaced = pins.remove(key);
		pins.put(key, pin);
		if (replaced != null) {
			return replaced != pin ? replaced : null;
		}
		if (pins.size() <= capacity) {
			return null;
		}
		Iterator<V> longestHidden = pins.values().iterator();
		V dropped = longestHidden.next();
		longestHidden.remove();
		return dropped;
	}

	/**
	 * Takes a pin out, to show it again or to drop it.
	 *
	 * @return the pin kept for the key, null when none is
	 */
	V take(K key) {
		return pins.remove(key);
	}

	/**
	 * Takes out the pins of the keys matching, as the ones whose drawing no longer holds.
	 *
	 * @return the pins taken out, the longest hidden first
	 */
	List<V> takeIf(Predicate<? super K> matches) {
		List<V> taken = new ArrayList<>();
		for (Iterator<Map.Entry<K, V>> entries = pins.entrySet().iterator(); entries.hasNext(); ) {
			Map.Entry<K, V> entry = entries.next();
			if (matches.test(entry.getKey())) {
				taken.add(entry.getValue());
				entries.remove();
			}
		}
		return taken;
	}

	int size() {
		return pins.size();
	}
}
