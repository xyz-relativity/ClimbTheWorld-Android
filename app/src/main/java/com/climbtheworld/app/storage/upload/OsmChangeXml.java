package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** The XML documents of the OSM API 0.6 that an upload sends. */
public final class OsmChangeXml {
	/** OSM keeps 7 decimals. */
	private static final int COORDINATE_SCALE = 7;

	private OsmChangeXml() {
	}

	/** The body creating a changeset with these tags. */
	public static String changeset(Map<String, String> tags) {
		StringBuilder xml = new StringBuilder("<osm><changeset>");
		for (Map.Entry<String, String> tag : tags.entrySet()) {
			appendTag(xml, tag.getKey(), tag.getValue());
		}
		return xml.append("</changeset></osm>").toString();
	}

	/**
	 * The osmChange document of prepared changes, which OSM applies all or nothing.
	 *
	 * @param changes in upload order, see UploadPlan
	 */
	public static String osmChange(List<PendingChange> changes, long changesetId, String generator) {
		StringBuilder xml = new StringBuilder("<osmChange version=\"0.6\" generator=\"")
				.append(escape(generator)).append("\">");
		for (PendingChange.Action action : PendingChange.Action.values()) {
			List<PendingChange> block = new ArrayList<>();
			for (PendingChange change : changes) {
				if (change.action == action) {
					block.add(change);
				}
			}
			if (block.isEmpty()) {
				continue;
			}
			xml.append('<').append(action.name()).append('>');
			for (PendingChange change : block) {
				appendElement(xml, change, changesetId);
			}
			xml.append("</").append(action.name()).append('>');
		}
		return xml.append("</osmChange>").toString();
	}

	private static void appendElement(StringBuilder xml, PendingChange change, long changesetId) {
		JSONObject element = change.upload;
		xml.append('<').append(change.type)
				.append(" id=\"").append(change.id)
				.append("\" changeset=\"").append(changesetId).append('"');
		if (change.action != PendingChange.Action.create) {
			xml.append(" version=\"").append(change.version).append('"');
		}
		if (element.has(ClimbingTags.KEY_LAT) && element.has(ClimbingTags.KEY_LON)) {
			xml.append(" lat=\"").append(coordinate(element.optDouble(ClimbingTags.KEY_LAT)))
					.append("\" lon=\"").append(coordinate(element.optDouble(ClimbingTags.KEY_LON)))
					.append('"');
		}
		if (change.action == PendingChange.Action.delete) {
			xml.append("/>");
			return;
		}
		xml.append('>');

		JSONArray nodes = element.optJSONArray(ClimbingTags.KEY_NODES);
		for (int index = 0; nodes != null && index < nodes.length(); index++) {
			xml.append("<nd ref=\"").append(nodes.optLong(index)).append("\"/>");
		}
		JSONArray members = element.optJSONArray(ClimbingTags.KEY_MEMBERS);
		for (int index = 0; members != null && index < members.length(); index++) {
			JSONObject member = members.optJSONObject(index);
			if (member == null) {
				continue;
			}
			xml.append("<member type=\"").append(escape(member.optString(ClimbingTags.KEY_TYPE)))
					.append("\" ref=\"").append(member.optLong(ClimbingTags.KEY_REF))
					.append("\" role=\"").append(escape(member.optString(ClimbingTags.KEY_ROLE)))
					.append("\"/>");
		}
		JSONObject tags = element.optJSONObject(ClimbingTags.KEY_TAGS);
		if (tags != null) {
			TreeSet<String> keys = new TreeSet<>();
			Iterator<String> iterator = tags.keys();
			while (iterator.hasNext()) {
				keys.add(iterator.next());
			}
			for (String key : keys) {
				appendTag(xml, key, tags.optString(key));
			}
		}
		xml.append("</").append(change.type).append('>');
	}

	private static void appendTag(StringBuilder xml, String key, String value) {
		xml.append("<tag k=\"").append(escape(key)).append("\" v=\"").append(escape(value))
				.append("\"/>");
	}

	/** Plain decimals, as 1.0E-5 is not a coordinate the API reads. */
	static String coordinate(double value) {
		return BigDecimal.valueOf(value).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
				.stripTrailingZeros().toPlainString();
	}

	/** For attribute values; line breaks stay line breaks. */
	static String escape(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '&':
					result.append("&amp;");
					break;
				case '<':
					result.append("&lt;");
					break;
				case '>':
					result.append("&gt;");
					break;
				case '"':
					result.append("&quot;");
					break;
				case '\'':
					result.append("&apos;");
					break;
				case '\n':
					result.append("&#10;");
					break;
				case '\r':
					result.append("&#13;");
					break;
				case '\t':
					result.append("&#9;");
					break;
				default:
					result.append(character);
			}
		}
		return result.toString();
	}
}
