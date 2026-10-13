package com.climbtheworld.app.storage.upload;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

// The real org.json, which plain unit tests only have as a stub.
@RunWith(RobolectricTestRunner.class)
public class OsmChangeXmlTest {
	@Test
	public void changesetCarriesItsTags() {
		Map<String, String> tags = new LinkedHashMap<>();
		tags.put("comment", "Add routes at \"Le Mur\" & more");
		tags.put("created_by", "ClimbTheWorld 2026.10");

		assertEquals("<osm><changeset>"
						+ "<tag k=\"comment\" v=\"Add routes at &quot;Le Mur&quot; &amp; more\"/>"
						+ "<tag k=\"created_by\" v=\"ClimbTheWorld 2026.10\"/>"
						+ "</changeset></osm>",
				OsmChangeXml.changeset(tags));
	}

	@Test
	public void osmChangeHasOneBlockPerActionWithVersionsOutsideCreate() throws Exception {
		PendingChange created = new PendingChange("node", -1, PendingChange.Action.create,
				new JSONObject("{\"type\":\"node\",\"id\":-1,\"lat\":45.1234567,\"lon\":-0.00001,"
						+ "\"tags\":{\"name\":\"A <b>\",\"description\":\"line 1\\nline 2\",\"climbing\":\"route_bottom\"}}"), null);
		PendingChange modified = new PendingChange("relation", 10, PendingChange.Action.modify,
				new JSONObject("{\"type\":\"relation\",\"id\":10,\"members\":["
						+ "{\"type\":\"node\",\"ref\":-1,\"role\":\"\"},{\"type\":\"node\",\"ref\":\"5\",\"role\":\"x\"}],"
						+ "\"tags\":{\"type\":\"site\"}}"), new JSONObject("{}"));
		modified.version = 3;
		PendingChange deleted = new PendingChange("node", 7, PendingChange.Action.delete,
				new JSONObject("{\"type\":\"node\",\"id\":7,\"lat\":1,\"lon\":2,\"tags\":{\"a\":\"b\"}}"),
				new JSONObject("{}"));
		deleted.version = 9;

		assertEquals("<osmChange version=\"0.6\" generator=\"ClimbTheWorld\">"
						+ "<create><node id=\"-1\" changeset=\"42\" lat=\"45.1234567\" lon=\"-0.00001\">"
						+ "<tag k=\"climbing\" v=\"route_bottom\"/>"
						+ "<tag k=\"description\" v=\"line 1&#10;line 2\"/>"
						+ "<tag k=\"name\" v=\"A &lt;b&gt;\"/></node></create>"
						+ "<modify><relation id=\"10\" changeset=\"42\" version=\"3\">"
						+ "<member type=\"node\" ref=\"-1\" role=\"\"/><member type=\"node\" ref=\"5\" role=\"x\"/>"
						+ "<tag k=\"type\" v=\"site\"/></relation></modify>"
						+ "<delete><node id=\"7\" changeset=\"42\" version=\"9\" lat=\"1\" lon=\"2\"/></delete>"
						+ "</osmChange>",
				OsmChangeXml.osmChange(Arrays.asList(created, modified, deleted), 42, "ClimbTheWorld"));
	}

	@Test
	public void coordinatesArePlainWithSevenDecimals() {
		assertEquals("0.00001", OsmChangeXml.coordinate(1.0E-5));
		assertEquals("45.1234568", OsmChangeXml.coordinate(45.12345678));
		assertEquals("0", OsmChangeXml.coordinate(0));
		assertEquals("-73", OsmChangeXml.coordinate(-73.0));
	}
}
