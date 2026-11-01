package com.climbtheworld.app.storage.logbook;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Lays out log book entries under the headings of their places, country, then area, then crag,
 * as the log book list shows them. A group lists its own entries, in the order given, before
 * its subgroups, unless it is collapsed.
 */
public final class LogBookGroups {
	// Between the keys of a group's path in its id; no key has a line break.
	private static final String ID_SEPARATOR = "\n";

	private LogBookGroups() {
		//hide constructor
	}

	/**
	 * @return the rows with every group expanded
	 * @see #rows(List, Function, Predicate)
	 */
	public static <T> List<Row<T>> rows(List<T> items, Function<T, List<LogBookGroup>> pathOf) {
		return rows(items, pathOf, groupId -> false);
	}

	/**
	 * @param pathOf    an item's groups, from the top level down; groups with the same key at the
	 *                  same place in the paths are the same group
	 * @param collapsed whether the group of an id, as {@link Row#groupId()} gives it, shows its
	 *                  heading only
	 * @return the rows of the list: each group's heading, followed by its entries and subgroups
	 */
	public static <T> List<Row<T>> rows(List<T> items, Function<T, List<LogBookGroup>> pathOf,
	                                    Predicate<String> collapsed) {
		Node<T> root = new Node<>(null, null);
		for (T item : items) {
			Node<T> node = root;
			for (LogBookGroup group : pathOf.apply(item)) {
				Node<T> parent = node;
				node = node.children.computeIfAbsent(group.key(), key -> new Node<>(group,
						parent.id == null ? key : parent.id + ID_SEPARATOR + key));
				node.entryCount++;
			}
			node.items.add(item);
		}

		List<Row<T>> result = new ArrayList<>();
		addRows(root, 0, result, byKindThenName(), collapsed);
		return result;
	}

	private static <T> void addRows(Node<T> node, int depth, List<Row<T>> result,
	                                Comparator<LogBookGroup> order, Predicate<String> collapsed) {
		for (T item : node.items) {
			result.add(new Row<>(null, item, depth, null, 0, false));
		}
		List<Node<T>> children = new ArrayList<>(node.children.values());
		children.sort((a, b) -> order.compare(a.group, b.group));
		for (Node<T> child : children) {
			boolean isCollapsed = collapsed.test(child.id);
			result.add(new Row<>(child.group, null, depth, child.id, child.entryCount,
					isCollapsed));
			if (!isCollapsed) {
				addRows(child, depth + 1, result, order, collapsed);
			}
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
	 * @param depth      how many groups the row is in, so 0 for a country's heading
	 * @param groupId    a heading's group by the keys of its path, as a key only tells groups
	 *                   apart at the same place in the paths; null for an entry
	 * @param entryCount how many entries a heading's group holds, its subgroups' included
	 * @param collapsed  whether a heading's entries and subgroups are left out
	 */
	public record Row<T>(LogBookGroup group, T item, int depth, String groupId, int entryCount,
	                     boolean collapsed) {
		public boolean isHeading() {
			return group != null;
		}
	}

	private static final class Node<T> {
		private final LogBookGroup group;
		private final String id;
		private final List<T> items = new ArrayList<>();
		private final Map<String, Node<T>> children = new LinkedHashMap<>();

		private int entryCount = 0;

		private Node(LogBookGroup group, String id) {
			this.group = group;
			this.id = id;
		}
	}
}
