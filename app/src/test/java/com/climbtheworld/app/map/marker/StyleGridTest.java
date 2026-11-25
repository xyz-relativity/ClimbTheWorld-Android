package com.climbtheworld.app.map.marker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.climbtheworld.app.storage.database.GeoNode;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StyleGridTest {
	@Test
	public void theStylesAreListedOffThePictogramRopedRowsFirst() {
		// The top row, then the bottom one, then the middle one, each left to right.
		assertEquals(Arrays.asList(GeoNode.ClimbingStyle.sport, GeoNode.ClimbingStyle.toprope,
						GeoNode.ClimbingStyle.trad, GeoNode.ClimbingStyle.ice,
						GeoNode.ClimbingStyle.multipitch, GeoNode.ClimbingStyle.mixed,
						GeoNode.ClimbingStyle.boulder, GeoNode.ClimbingStyle.deepwater),
				StyleGrid.all());
	}

	@Test
	public void theEnumIsDeclaredInTheSameOrder() {
		assertEquals(StyleGrid.all(), Arrays.asList(GeoNode.ClimbingStyle.values()));
	}

	@Test
	public void everyStyleHasACellOfItsOwn() {
		// The centre cell is the rope dot's.
		Set<String> taken = new HashSet<>(Collections.singleton("1,1"));
		for (GeoNode.ClimbingStyle style : GeoNode.ClimbingStyle.values()) {
			StyleGrid.Cell cell = StyleGrid.cell(style);
			assertNotNull(style.name(), cell);
			assertTrue(style.name(), taken.add(cell.column() + "," + cell.row()));
		}
	}

	@Test
	public void sortingLeavesTheGivenListAlone() {
		List<GeoNode.ClimbingStyle> given = Arrays.asList(GeoNode.ClimbingStyle.deepwater,
				GeoNode.ClimbingStyle.trad, GeoNode.ClimbingStyle.boulder,
				GeoNode.ClimbingStyle.sport);

		assertEquals(Arrays.asList(GeoNode.ClimbingStyle.sport, GeoNode.ClimbingStyle.trad,
				GeoNode.ClimbingStyle.boulder, GeoNode.ClimbingStyle.deepwater),
				StyleGrid.sorted(given));
		assertEquals(GeoNode.ClimbingStyle.deepwater, given.get(0));
	}
}
