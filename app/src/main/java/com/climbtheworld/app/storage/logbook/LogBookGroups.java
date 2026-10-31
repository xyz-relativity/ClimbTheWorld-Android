package com.climbtheworld.app.storage.logbook;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Lays out log book entries under the headings of their places, country, then area, then crag,
 * as the log book list shows them. A group lists its own entries, in the order given, before
 * its subgroups.
 */
public final class LogBookGroups {
	private LogBookGroups() {
		//hide constructor
	}

	/**
	 * @param pathOf an item's groups, from the top level down; groups with the same key at the
	 *               same place in the paths are the same group
	 * @return the rows of the list: each group's heading, followed by its entries and subgroups
	 */
	public static <T> List<Row<T>> rows(List<T> items, Function<T, List<LogBookGroup>> pathOf) {
		Node<T> root = new Node<>(null);
		for (T item : items) {
			Node<T> node = root;
			for (LogBookGroup group : pathOf.apply(item)) {
				node = node.children.computeIfAbsent(group.key(), key -> new Node<>(group));
			}
			node.items.add(item);
		}

		List<Row<T>> result = new ArrayList<>();
		addRows(root, 0, result, byKindThenName());
		return result;
	}

	private static <T> void addRows(Node<T> node, int depth, List<Row<T>> result,
	                                Comparator<LogBookGroup> order) {
		for (T item : node.items) {
			result.add(new Row<>(null, item, depth));
		}
		List<Node<T>> children = new ArrayList<>(node.children.values());
		children.sort((a, b) -> order.compare(a.group, b.group));
		for (Node<T> child : children) {
			result.add(new Row<>(child.group, null, depth));
			addRows(child, depth + 1, result, order);
		}
	}

	private static Comparator<LogBookGroup> byKindThenName() {
		// Sorts "Écrins" with the other E's, not after Z.
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		return Comparator.comparing(LogBookGroup::kind)
				.thenComparing(LogBookGroup::name, collator);
	}

	/**
	 * A row of the list: a group's heading, or an entry.
	 *
	 * @param depth how many groups the row is in, so 0 for a country's heading
	 */
	public record Row<T>(LogBookGroup group, T item, int depth) {
		public boolean isHeading() {
			return group != null;
		}
	}

	private static final class Node<T> {
		private final LogBookGroup group;
		private final List<T> items = new ArrayList<>();
		private final Map<String, Node<T>> children = new LinkedHashMap<>();

		private Node(LogBookGroup group) {
			this.group = group;
		}
	}
}
