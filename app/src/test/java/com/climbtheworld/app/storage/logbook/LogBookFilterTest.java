package com.climbtheworld.app.storage.logbook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.database.GeoNode;
import com.climbtheworld.app.storage.database.OsmEntity;

import org.junit.Test;

import java.util.EnumSet;

public class LogBookFilterTest {
	@Test
	public void anEmptyFilterMatchesEverything() {
		assertTrue(new LogBookFilter().matches(entry("Été indien", "", GeoNode.NodeTypes.route,
				LogBookEntry.Attempt.none)));
	}

	@Test
	public void textMatchesTheNameOrTheNoteIgnoringCaseAndAccents() {
		LogBookEntry entry = entry("Été Indien", "Crux at the third BOLT",
				GeoNode.NodeTypes.route, LogBookEntry.Attempt.sent);
		LogBookFilter filter = new LogBookFilter();

		filter.setText("ete ind");
		assertTrue(filter.matches(entry));
		filter.setText("  third bolt ");
		assertTrue(filter.matches(entry));
		filter.setText("slab");
		assertFalse(filter.matches(entry));
	}

	@Test
	public void entriesWithoutASnapshotNameAreStillFoundByTheirNote() {
		LogBookEntry entry = entry(null, "Wet after rain", GeoNode.NodeTypes.crag,
				LogBookEntry.Attempt.none);
		LogBookFilter filter = new LogBookFilter();

		filter.setText("rain");

		assertTrue(filter.matches(entry));
	}

	@Test
	public void statusFilterAcceptsAnyOfTheChosenAttempts() {
		LogBookFilter filter = new LogBookFilter();
		filter.setAttempts(EnumSet.complementOf(EnumSet.of(LogBookEntry.Attempt.none)));

		assertTrue(filter.matches(entry("A", "", GeoNode.NodeTypes.route,
				LogBookEntry.Attempt.notCompleted)));
		assertFalse(filter.matches(entry("B", "note", GeoNode.NodeTypes.route,
				LogBookEntry.Attempt.none)));
	}

	@Test
	public void typeFilterUsesTheSnapshotTypeAndTreatsUnknownTypesAsUnknown() {
		LogBookEntry crag = entry("Crag", "", GeoNode.NodeTypes.crag, LogBookEntry.Attempt.none);
		LogBookEntry renamedType = entry("Old", "", GeoNode.NodeTypes.crag,
				LogBookEntry.Attempt.none);
		renamedType.nodeType = "typeThatNoLongerExists";
		LogBookFilter filter = new LogBookFilter();

		filter.setNodeType(GeoNode.NodeTypes.crag);
		assertTrue(filter.matches(crag));
		assertFalse(filter.matches(renamedType));

		filter.setNodeType(GeoNode.NodeTypes.unknown);
		assertTrue(filter.matches(renamedType));
	}

	private static LogBookEntry entry(String name, String note, GeoNode.NodeTypes type,
	                                  LogBookEntry.Attempt attempt) {
		LogBookEntry entry = new LogBookEntry(OsmEntity.EntityOsmType.node, 1L);
		entry.name = name;
		entry.note = note;
		entry.nodeType = type.name();
		entry.attempt = attempt;
		return entry;
	}
}
