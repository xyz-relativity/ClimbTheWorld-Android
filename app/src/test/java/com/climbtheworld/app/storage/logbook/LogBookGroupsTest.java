package com.climbtheworld.app.storage.logbook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogBookGroupsTest {
	private static final LogBookGroup FRANCE = group(LogBookGroup.Kind.country, "FR", "France");
	private static final LogBookGroup SPAIN = group(LogBookGroup.Kind.country, "ES", "Spain");
	private static final LogBookGroup UNKNOWN_COUNTRY =
			group(LogBookGroup.Kind.unknownCountry, "unknownCountry", "Unknown country");
	private static final LogBookGroup NOT_DOWNLOADED =
			group(LogBookGroup.Kind.notDownloaded, "notDownloaded", "Not downloaded");
	private static final LogBookGroup ECRINS = group(LogBookGroup.Kind.area, "relation|1", "Écrins");
	private static final LogBookGroup VERDON = group(LogBookGroup.Kind.area, "relation|2", "Verdon");
	private static final LogBookGroup AILEFROIDE =
			group(LogBookGroup.Kind.crag, "relation|3", "Ailefroide");
	private static final LogBookGroup ESCALES = group(LogBookGroup.Kind.crag, "relation|4", "Escalès");
	private static final LogBookGroup BUOUX = group(LogBookGroup.Kind.crag, "relation|5", "Buoux");

	@Test
	public void listsEachGroupOnceWithItsEntriesUnderItsHeading() {
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("Ula", Arrays.asList(FRANCE, VERDON, ESCALES));
		paths.put("Ailefroide's own entry", Arrays.asList(FRANCE, ECRINS, AILEFROIDE));
		paths.put("Dalle", Arrays.asList(FRANCE, ECRINS, AILEFROIDE));
		paths.put("Pilier", Arrays.asList(FRANCE, VERDON, ESCALES));

		assertEquals(Arrays.asList(
						"# France",
						"  # Écrins",
						"    # Ailefroide",
						"      Ailefroide's own entry",
						"      Dalle",
						"  # Verdon",
						"    # Escalès",
						"      Ula",
						"      Pilier"),
				render(Arrays.asList("Ula", "Ailefroide's own entry", "Dalle", "Pilier"), paths));
	}

	@Test
	public void entriesOfAGroupComeBeforeItsSubgroupsAndAreasBeforeLooseCrags() {
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("Buoux route", Arrays.asList(FRANCE, BUOUX));
		paths.put("Gym", List.of(FRANCE));
		paths.put("Verdon area", Arrays.asList(FRANCE, VERDON));
		paths.put("Loose route in Verdon", Arrays.asList(FRANCE, VERDON));

		assertEquals(Arrays.asList(
						"# France",
						"  Gym",
						"  # Verdon",
						"    Verdon area",
						"    Loose route in Verdon",
						"  # Buoux",
						"    Buoux route"),
				render(Arrays.asList("Buoux route", "Gym", "Verdon area", "Loose route in Verdon"),
						paths));
	}

	@Test
	public void countriesSortByNameWithUnknownAndNotDownloadedLast() {
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("Gone", List.of(NOT_DOWNLOADED));
		paths.put("Nowhere", List.of(UNKNOWN_COUNTRY));
		paths.put("Siurana", List.of(SPAIN));
		paths.put("Céüse", List.of(FRANCE));

		assertEquals(Arrays.asList(
						"# France", "  Céüse",
						"# Spain", "  Siurana",
						"# Unknown country", "  Nowhere",
						"# Not downloaded", "  Gone"),
				render(Arrays.asList("Gone", "Nowhere", "Siurana", "Céüse"), paths));
	}

	@Test
	public void groupsOfTheSameNameStayApart() {
		LogBookGroup otherAilefroide = group(LogBookGroup.Kind.crag, "relation|9", "Ailefroide");
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("A", Arrays.asList(FRANCE, AILEFROIDE));
		paths.put("B", Arrays.asList(FRANCE, otherAilefroide));

		assertEquals(Arrays.asList(
						"# France",
						"  # Ailefroide",
						"    A",
						"  # Ailefroide",
						"    B"),
				render(Arrays.asList("A", "B"), paths));
	}

	@Test
	public void collapsedGroupShowsItsHeadingWithItsEntryCountOnly() {
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("Ula", Arrays.asList(FRANCE, VERDON, ESCALES));
		paths.put("Pilier", Arrays.asList(FRANCE, VERDON, ESCALES));
		paths.put("Verdon area", Arrays.asList(FRANCE, VERDON));
		paths.put("Dalle", Arrays.asList(FRANCE, ECRINS, AILEFROIDE));
		List<String> items = Arrays.asList("Ula", "Pilier", "Verdon area", "Dalle");
		Set<String> collapsed = new HashSet<>();
		collapsed.add(idOf(items, paths, VERDON));

		assertEquals(Arrays.asList(
						"# France (4)",
						"  # Écrins (1)",
						"    # Ailefroide (1)",
						"      Dalle",
						"  > Verdon (3)"),
				renderWithCounts(items, paths, collapsed));
	}

	@Test
	public void collapsingAGroupLeavesTheSameGroupElsewhereOpen() {
		Map<String, List<LogBookGroup>> paths = new HashMap<>();
		paths.put("A", Arrays.asList(FRANCE, BUOUX));
		paths.put("B", Arrays.asList(SPAIN, BUOUX));
		List<String> items = Arrays.asList("A", "B");
		Set<String> collapsed = new HashSet<>();
		// The first Buoux, France's.
		collapsed.add(idOf(items, paths, BUOUX));

		assertEquals(Arrays.asList(
						"# France (1)",
						"  > Buoux (1)",
						"# Spain (1)",
						"  # Buoux (1)",
						"    B"),
				renderWithCounts(items, paths, collapsed));
	}

	@Test
	public void emptyLogBookHasNoRows() {
		assertEquals(new ArrayList<String>(), render(new ArrayList<>(), new HashMap<>()));
	}

	private static List<String> render(List<String> items, Map<String, List<LogBookGroup>> paths) {
		List<String> result = new ArrayList<>();
		for (LogBookGroups.Row<String> row : LogBookGroups.rows(items, paths::get)) {
			String indent = "  ".repeat(row.depth());
			result.add(row.isHeading() ? indent + "# " + row.group().name() : indent + row.item());
		}
		return result;
	}

	private static List<String> renderWithCounts(List<String> items,
	                                             Map<String, List<LogBookGroup>> paths,
	                                             Set<String> collapsed) {
		List<String> result = new ArrayList<>();
		for (LogBookGroups.Row<String> row : LogBookGroups.rows(items, paths::get, collapsed::contains)) {
			String indent = "  ".repeat(row.depth());
			result.add(row.isHeading()
					? indent + (row.collapsed() ? "> " : "# ") + row.group().name()
					+ " (" + row.entryCount() + ")"
					: indent + row.item());
		}
		return result;
	}

	private static String idOf(List<String> items, Map<String, List<LogBookGroup>> paths,
	                           LogBookGroup group) {
		for (LogBookGroups.Row<String> row : LogBookGroups.rows(items, paths::get)) {
			if (row.group() == group) {
				return row.groupId();
			}
		}
		throw new AssertionError(group.name() + " is not listed");
	}

	private static LogBookGroup group(LogBookGroup.Kind kind, String key, String name) {
		return new LogBookGroup(kind, key, name);
	}
}
