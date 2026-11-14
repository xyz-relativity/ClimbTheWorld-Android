package com.climbtheworld.app.map.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * What an editor would save at one moment, to tell whether the user changed anything since: the
 * editor takes one once it has loaded, and compares it with what it would save when the user is
 * about to leave without saving.
 * <p>
 * Taken as text at once, so that changing the JSON afterwards, as the editors do in place, leaves
 * it as it was. Two snapshots of the same content are equal whatever order the keys of its JSON
 * objects are in, whatever type its numbers were read as (1 and 1.0 alike, as JSON read back from
 * text has them), and with an empty or null value the same as no value, as the form writes an
 * empty field by leaving the tag out.
 */
public final class EditSnapshot {
	private final String content;

	private EditSnapshot(String content) {
		this.content = content;
	}

	/**
	 * @param content a JSONObject, a JSONArray, or a collection of them, as JSON values in turn
	 */
	public static EditSnapshot of(Object content) {
		StringBuilder out = new StringBuilder();
		write(content, out);
		return new EditSnapshot(out.toString());
	}

	/**
	 * Whether the content differs from the snapshot taken when the editor loaded. Without a
	 * snapshot, or with content that can't be read now (null, as from a field that doesn't parse),
	 * a change may be hidden, so it counts as changed: asking the user once too often loses
	 * nothing.
	 */
	public static boolean changed(EditSnapshot loaded, Object current) {
		return loaded == null || current == null || !loaded.equals(of(current));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EditSnapshot && content.equals(((EditSnapshot) other).content);
	}

	@Override
	public int hashCode() {
		return content.hashCode();
	}

	private static void write(Object value, StringBuilder out) {
		if (value instanceof JSONObject) {
			writeObject((JSONObject) value, out);
		} else if (value instanceof JSONArray) {
			JSONArray array = (JSONArray) value;
			List<Object> items = new ArrayList<>();
			for (int index = 0; index < array.length(); index++) {
				items.add(array.opt(index));
			}
			writeArray(items, out);
		} else if (value instanceof Collection) {
			writeArray((Collection<?>) value, out);
		} else if (value instanceof Number) {
			out.append(number((Number) value));
		} else if (value instanceof String) {
			out.append(JSONObject.quote((String) value));
		} else if (value == null || value == JSONObject.NULL) {
			out.append("null");
		} else {
			out.append(value);
		}
	}

	private static void writeObject(JSONObject object, StringBuilder out) {
		List<String> keys = new ArrayList<>();
		Iterator<String> iterator = object.keys();
		while (iterator.hasNext()) {
			String key = iterator.next();
			if (!isEmpty(object.opt(key))) {
				keys.add(key);
			}
		}
		Collections.sort(keys);
		out.append('{');
		for (int index = 0; index < keys.size(); index++) {
			if (index > 0) {
				out.append(',');
			}
			out.append(JSONObject.quote(keys.get(index))).append(':');
			write(object.opt(keys.get(index)), out);
		}
		out.append('}');
	}

	/** In their order, which for a relation's members is part of what is saved. */
	private static void writeArray(Collection<?> items, StringBuilder out) {
		out.append('[');
		boolean first = true;
		for (Object item : items) {
			if (!first) {
				out.append(',');
			}
			first = false;
			write(item, out);
		}
		out.append(']');
	}

	private static boolean isEmpty(Object value) {
		return value == null || value == JSONObject.NULL
				|| (value instanceof String && ((String) value).isEmpty());
	}

	private static String number(Number number) {
		BigDecimal decimal;
		if (number instanceof Double || number instanceof Float) {
			double value = number.doubleValue();
			if (Double.isNaN(value) || Double.isInfinite(value)) {
				return Double.toString(value);
			}
			decimal = BigDecimal.valueOf(value);
		} else if (number instanceof BigDecimal) {
			decimal = (BigDecimal) number;
		} else {
			decimal = new BigDecimal(number.toString());
		}
		return decimal.stripTrailingZeros().toPlainString();
	}
}
