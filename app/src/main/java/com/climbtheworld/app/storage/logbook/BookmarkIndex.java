package com.climbtheworld.app.storage.logbook;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The bookmarked elements as the "bookmarked only" display filter lets them through: each
 * bookmarked element along with what it is made of, so the routes of a bookmarked crag or area
 * and the pitches of a bookmarked route relation.
 *
 * <p>A route mapped as a way is opened, so bookmarked, as the node at its bottom, while its crag
 * counts it as the way; the two go together, so that the crag counts the route as the map
 * shows it. Only its bottom: its top can be the bottom of another route.</p>
 *
 * <p>Elements are named by their key, "type|id" as RelationMembers.key. Two indexes are equal
 * only when they are the same object; see sameAs.</p>
 */
public final class BookmarkIndex {
	public static final BookmarkIndex EMPTY =
			new BookmarkIndex(Collections.emptySet(), Collections.emptySet());
	private static final String NODE_PREFIX = "node|";

	private final Set<String> bookmarked;
	private final Set<String> shown;

	private BookmarkIndex(Set<String> bookmarked, Set<String> shown) {
		this.bookmarked = bookmarked;
		this.shown = shown;
	}

	/**
	 * What the elements are made of, as the OSM data on the device tells.
	 */
	public interface Parts {
		/**
		 * @return the keys of what the element is made of: a relation's members, a route way's
		 * bottom node, any other way's nodes; none for a node, or for an element not on the
		 * device
		 */
		Collection<String> of(String key);

		/**
		 * @return the keys of the routes mapped as a way that start at the node
		 */
		Collection<String> routeWaysFrom(long nodeId);
	}

	/**
	 * @param bookmarkedKeys the keys of the bookmarked elements
	 */
	public static BookmarkIndex build(Collection<String> bookmarkedKeys, Parts parts) {
		if (bookmarkedKeys.isEmpty()) {
			return EMPTY;
		}

		Set<String> shown = new HashSet<>();
		Deque<String> pending = new ArrayDeque<>();
		for (String key : bookmarkedKeys) {
			pending.add(key);
			if (key.startsWith(NODE_PREFIX)) {
				pending.addAll(parts.routeWaysFrom(
						Long.parseLong(key.substring(NODE_PREFIX.length()))));
			}
		}
		while (!pending.isEmpty()) {
			String key = pending.pop();
			// The visited check also ends loops, as relations holding each other.
			if (shown.add(key)) {
				pending.addAll(parts.of(key));
			}
		}
		return new BookmarkIndex(Collections.unmodifiableSet(new HashSet<>(bookmarkedKeys)),
				Collections.unmodifiableSet(shown));
	}

	/**
	 * Names an element, as RelationMembers.key.
	 */
	public static String key(String osmType, long osmId) {
		return osmType + "|" + osmId;
	}

	/**
	 * @return whether the element is bookmarked itself
	 */
	public boolean isBookmarked(String key) {
		return bookmarked.contains(key);
	}

	/**
	 * @return whether the "bookmarked only" display filter lets the element through: it is
	 * bookmarked, or part of what is
	 */
	public boolean shows(String key) {
		return shown.contains(key);
	}

	public boolean isEmpty() {
		return bookmarked.isEmpty();
	}

	/**
	 * @return whether the two hold the same bookmarks and let the same elements through. A
	 * reload that changes nothing keeps the index it had, so what was worked out with it, as the
	 * filter results the A.R. view keeps, still holds.
	 */
	public boolean sameAs(BookmarkIndex other) {
		return bookmarked.equals(other.bookmarked) && shown.equals(other.shown);
	}
}
