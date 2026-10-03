package com.climbtheworld.app.storage.logbook;

import androidx.annotation.Nullable;

import com.climbtheworld.app.storage.database.GeoNode;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which log book entries to list. Text is looked up in the element name and the note, ignoring
 * case and accents, so "ete" finds "Été".
 */
public class LogBookFilter {
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private String text = "";
	private Set<LogBookEntry.Attempt> attempts = EnumSet.noneOf(LogBookEntry.Attempt.class);
	private GeoNode.NodeTypes nodeType = null;

	public void setText(String text) {
		this.text = normalize(text.trim());
	}

	/**
	 * @param attempts the accepted climbed statuses; empty accepts any.
	 */
	public void setAttempts(Set<LogBookEntry.Attempt> attempts) {
		this.attempts = EnumSet.noneOf(LogBookEntry.Attempt.class);
		this.attempts.addAll(attempts);
	}

	/**
	 * @param nodeType the accepted element type; null accepts any.
	 */
	public void setNodeType(@Nullable GeoNode.NodeTypes nodeType) {
		this.nodeType = nodeType;
	}

	public boolean matches(LogBookEntry entry) {
		if (!attempts.isEmpty() && !attempts.contains(entry.attempt)) {
			return false;
		}
		if (nodeType != null && entry.getElementType() != nodeType) {
			return false;
		}
		return text.isEmpty()
				|| normalize(entry.name).contains(text)
				|| normalize(entry.note).contains(text);
	}

	static String normalize(@Nullable String value) {
		if (value == null) {
			return "";
		}
		return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
				.replaceAll("").toLowerCase(Locale.ROOT);
	}
}
