package com.climbtheworld.app.storage.upload;

import com.climbtheworld.app.storage.database.ClimbingTags;
import com.climbtheworld.app.storage.edit.RelationMembers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * OSM's answer to an upload: the id each new element got, and for each element whether it still
 * exists. Deleted elements are listed without a new id.
 */
public final class DiffResult {
	private final Map<String, Long> newIds;
	private final Map<String, Boolean> kept;

	private DiffResult(Map<String, Long> newIds, Map<String, Boolean> kept) {
		this.newIds = newIds;
		this.kept = kept;
	}

	public static DiffResult parse(String xml) throws IOException {
		Map<String, Long> newIds = new HashMap<>();
		Map<String, Boolean> kept = new HashMap<>();
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			// Errors come through the exception, not the default handler's printing.
			builder.setErrorHandler(new DefaultHandler() {
				@Override
				public void fatalError(SAXParseException exception) throws SAXException {
					throw exception;
				}
			});
			NodeList elements = builder.parse(new InputSource(new StringReader(xml)))
					.getDocumentElement().getChildNodes();
			for (int index = 0; index < elements.getLength(); index++) {
				Node node = elements.item(index);
				if (node.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				Element element = (Element) node;
				String key = RelationMembers.key(element.getTagName(),
						Long.parseLong(element.getAttribute("old_id")));
				String newId = element.getAttribute("new_id");
				kept.put(key, !newId.isEmpty());
				if (!newId.isEmpty()) {
					newIds.put(key, Long.parseLong(newId));
				}
			}
		} catch (ParserConfigurationException | SAXException | NumberFormatException e) {
			throw new IOException("Can't read the upload result: " + e.getMessage(), e);
		}
		return new DiffResult(newIds, kept);
	}

	/** Whether OSM took the change of this element. */
	public boolean contains(String type, long oldId) {
		return kept.containsKey(RelationMembers.key(type, oldId));
	}

	/** The element's id on OSM: the new one for a created element, else the same. */
	public long newId(String type, long oldId) {
		Long newId = newIds.get(RelationMembers.key(type, oldId));
		return newId != null ? newId : oldId;
	}

	/** Points the element's id and its references to new elements at their OSM ids. */
	public void remap(JSONObject element) {
		String type = element.optString(ClimbingTags.KEY_TYPE);
		try {
			element.put(ClimbingTags.KEY_ID, newId(type, element.optLong(ClimbingTags.KEY_ID)));
			JSONArray members = element.optJSONArray(ClimbingTags.KEY_MEMBERS);
			for (int index = 0; members != null && index < members.length(); index++) {
				JSONObject member = members.optJSONObject(index);
				if (member != null) {
					member.put(ClimbingTags.KEY_REF, newId(member.optString(ClimbingTags.KEY_TYPE),
							member.optLong(ClimbingTags.KEY_REF)));
				}
			}
			JSONArray nodes = element.optJSONArray(ClimbingTags.KEY_NODES);
			for (int index = 0; nodes != null && index < nodes.length(); index++) {
				nodes.put(index, newId("node", nodes.optLong(index)));
			}
		} catch (JSONException e) {
			throw new IllegalStateException(e);
		}
	}
}
