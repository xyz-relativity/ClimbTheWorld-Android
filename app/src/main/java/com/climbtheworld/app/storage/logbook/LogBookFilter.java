package com.climbtheworld.app.storage.logbook;

import androidx.annotation.Nullable;

import com.climbtheworld.app.storage.database.OsmEntity;

import java.text.Normalizer;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which log book entries to list. Text is looked up in the element name, the note and the names
 * of the groups the entry is listed under, ignoring case and accents, so "ete" finds "Été".
 */
public class LogBookFilter {
	private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

	private String text = "";
	private Set<LogBookEntry.Attempt> attempts = EnumSet.noneOf(LogBookEntry.Attempt.class);
	private OsmEntity.EntityClimbingType nodeType = null;

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
	public void setNodeType(@Nullable OsmEntity.EntityClimbingType nodeType) {
		this.nodeType = nodeType;
	}

	public boolean matches(LogBookEntry entry) {
		return matches(entry, Collections.emptyList());
	}

	/**
	 * @param groups the groups the entry is listed under, see {@link LogBookGroups}
	 */
	public boolean matches(LogBookEntry entry, List<LogBookGroup> groups) {
		if (!attempts.isEmpty() && !attempts.contains(entry.attempt)) {
			return false;
		}
		if (nodeType != null && entry.getElementType() != nodeType) {
			return false;
		}
		if (text.isEmpty()
				|| normalize(entry.name).contains(text)
				|| normalize(entry.note).contains(text)) {
			return true;
		}
		for (LogBookGroup group : groups) {
			if (normalize(group.name()).contains(text)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the text in lower case and without accents, for looking up text in it.
	 */
	public static String normalize(@Nullable String value) {
		if (value == null) {
			return "";
		}
		return DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
				.replaceAll("").toLowerCase(Locale.ROOT);
	}
}
